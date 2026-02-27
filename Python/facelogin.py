import os, sys, time
import numpy as np
import cv2
from collections import deque

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

from deepface import DeepFace

DETECTOR_BACKENDS = ("opencv", "retinaface", "mtcnn")
_CASCADE = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")


def _extract_face_roi(frame_bgr):
    gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)
    faces = _CASCADE.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(80, 80))
    if len(faces) == 0:
        return None
    x, y, w, h = max(faces, key=lambda r: r[2] * r[3])
    pad = int(0.18 * max(w, h))
    x0 = max(0, x - pad)
    y0 = max(0, y - pad)
    x1 = min(frame_bgr.shape[1], x + w + pad)
    y1 = min(frame_bgr.shape[0], y + h + pad)
    return frame_bgr[y0:y1, x0:x1]


def _center_crop(frame_bgr):
    h, w = frame_bgr.shape[:2]
    side = int(min(h, w) * 0.72)
    cx, cy = w // 2, h // 2
    x0 = max(0, cx - side // 2)
    y0 = max(0, cy - side // 2)
    x1 = min(w, x0 + side)
    y1 = min(h, y0 + side)
    return frame_bgr[y0:y1, x0:x1]


def load_users_index_txt(path):
    items = []
    if not os.path.exists(path):
        return items
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or "|" not in line:
                continue
            email, face_path = line.split("|", 1)
            email = email.strip()
            face_path = face_path.strip()
            if email and face_path:
                items.append((email, face_path))
    return items


def load_embeddings(users_index_txt):
    rows = load_users_index_txt(users_index_txt)
    if not rows:
        return None, None, "no_enrolled_users"

    emails = []
    vecs = []
    skipped = 0

    for email, face_path in rows:
        root, _ = os.path.splitext(face_path)
        npy = root + ".npy"
        if not os.path.exists(npy):
            skipped += 1
            continue
        try:
            v = np.load(npy).astype(np.float32)
            v /= (np.linalg.norm(v) + 1e-12)
            vecs.append(v)
            emails.append(email)
        except Exception:
            skipped += 1

    if not vecs:
        return None, None, f"no_valid_face_images skipped={skipped}"

    X = np.stack(vecs, axis=0).astype(np.float32)   # (N, D)
    return X, emails, None


def compute_live_embedding(frame_bgr, model_name="ArcFace"):
    def _represent(image_rgb, backend, enforce, align):
        try:
            reps = DeepFace.represent(
                img_path=image_rgb,
                model_name=model_name,
                detector_backend=backend,
                enforce_detection=enforce,
                align=align,
            )
            if isinstance(reps, list):
                reps = reps[0]
            emb = np.array(reps["embedding"], dtype=np.float32)
            emb /= (np.linalg.norm(emb) + 1e-12)
            return emb
        except Exception as ex:
            msg = str(ex).lower()
            if "weights" in msg and ("download" in msg or "arcface" in msg):
                raise RuntimeError("model_weights_missing")
            raise

    frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    for backend in DETECTOR_BACKENDS:
        try:
            return _represent(frame_rgb, backend, True, True)
        except Exception:
            pass

    roi = _extract_face_roi(frame_bgr)
    if roi is not None:
        try:
            return _represent(cv2.cvtColor(roi, cv2.COLOR_BGR2RGB), "skip", False, False)
        except Exception:
            pass

    crop = _center_crop(frame_bgr)
    return _represent(cv2.cvtColor(crop, cv2.COLOR_BGR2RGB), "skip", False, False)


def main():
    if len(sys.argv) < 2:
        print("FAIL missing_index_file")
        sys.exit(2)

    users_index_txt = sys.argv[1]

    X, emails, err = load_embeddings(users_index_txt)
    if err:
        print(f"FAIL {err}")
        sys.exit(1)

    n_users = len(emails)

    # camera
    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not cap.isOpened():
        cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("FAIL camera_not_opened")
        sys.exit(1)

    for _ in range(12):
        cap.read()

    # ===== TUNING =====
    # Cosine similarity: higher = more similar
    SIM_THRESHOLD = 0.60

    # Margin: (best - second) must be >= this (only enforced when >=2 users)
    MARGIN_MIN = 0.05

    TIMEOUT_SECONDS = 20
    ROLLING = 6
    MAJORITY_NEED = 4
    MIN_FACE_FRAMES = 4

    sim_hist = deque(maxlen=ROLLING)
    margin_hist = deque(maxlen=ROLLING)
    email_hist = deque(maxlen=ROLLING)

    start = time.time()
    frames = 0

    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                print("FAIL frame_error")
                sys.exit(1)

            try:
                live = compute_live_embedding(frame, model_name="ArcFace")
            except Exception as ex:
                if "model_weights_missing" in str(ex):
                    print("FAIL model_weights_missing")
                    sys.exit(1)
                if (time.time() - start) > TIMEOUT_SECONDS:
                    print("FAIL no_face_detected")
                    sys.exit(1)
                continue

            frames += 1

            # cosine similarity (because both normalized):
            # sims[i] = dot(live, X[i])
            sims = (X @ live).astype(np.float32)   # (N,)

            best_i = int(np.argmax(sims))
            best_sim = float(sims[best_i])
            best_email = emails[best_i]

            if n_users >= 2:
                # get second best efficiently
                tmp = sims.copy()
                tmp[best_i] = -999.0
                second_sim = float(np.max(tmp))
                margin = best_sim - second_sim
            else:
                margin = 999.0

            sim_hist.append(best_sim)
            margin_hist.append(margin)
            email_hist.append(best_email)

            avg_sim = float(sum(sim_hist) / len(sim_hist))
            avg_margin = float(sum(margin_hist) / len(margin_hist))

            major_email = max(set(email_hist), key=email_hist.count)
            major_count = email_hist.count(major_email)

            if frames < MIN_FACE_FRAMES:
                if (time.time() - start) > TIMEOUT_SECONDS:
                    print(f"FAIL not_recognized sim={avg_sim:.3f} thr={SIM_THRESHOLD:.3f} margin={avg_margin:.3f}")
                    sys.exit(1)
                continue

            margin_ok = True if n_users <= 1 else (avg_margin >= MARGIN_MIN)
            ok_candidate = (avg_sim >= SIM_THRESHOLD) and margin_ok and (major_count >= MAJORITY_NEED)

            if ok_candidate:
                print(f"OK email={major_email} sim={avg_sim:.3f} margin={avg_margin:.3f}")
                sys.exit(0)

            if (time.time() - start) > TIMEOUT_SECONDS:
                print(f"FAIL not_recognized sim={avg_sim:.3f} thr={SIM_THRESHOLD:.3f} margin={avg_margin:.3f}")
                sys.exit(1)

    finally:
        cap.release()


if __name__ == "__main__":
    main()
