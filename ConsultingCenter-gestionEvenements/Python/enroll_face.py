import os, sys, time
import cv2
import numpy as np

# quieter TF / deepface logs
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


def compute_embedding_from_frame(frame_bgr, model_name="ArcFace"):
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
        print("FAIL missing_user_id")
        sys.exit(2)

    user_id = sys.argv[1]
    os.makedirs("faces", exist_ok=True)

    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not cap.isOpened():
        cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("FAIL camera_not_opened")
        sys.exit(1)

    # warmup
    for _ in range(15):
        cap.read()

    TARGET = 6              # embeddings to average
    CAPTURE_EVERY = 0.45    # seconds
    TIMEOUT = 120           # seconds

    emb_list = []
    preview_saved = False
    preview_path = os.path.abspath(os.path.join("faces", f"enroll_{user_id}.jpg"))
    emb_path = os.path.abspath(os.path.join("faces", f"enroll_{user_id}.npy"))

    last_t = 0.0
    start = time.time()

    # Enrollment window (you wanted it bigger)
    cv2.namedWindow("Enrollment", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Enrollment", 980, 620)

    while True:
        ok, frame = cap.read()
        if not ok:
            cap.release()
            cv2.destroyAllWindows()
            print("FAIL frame_error")
            sys.exit(1)

        now = time.time()

        if (now - last_t) >= CAPTURE_EVERY and len(emb_list) < TARGET:
            try:
                emb = compute_embedding_from_frame(frame, model_name="ArcFace")
                emb_list.append(emb)
                last_t = now

                if not preview_saved:
                    # Save a preview frame as JPG once
                    face_img = cv2.resize(frame, (240, 240))
                    cv2.imwrite(preview_path, face_img)
                    preview_saved = True
            except Exception as ex:
                if "model_weights_missing" in str(ex):
                    cap.release()
                    cv2.destroyAllWindows()
                    print("FAIL model_weights_missing")
                    sys.exit(1)
                # no face detected / alignment failed
                pass

        # UI
        cv2.putText(frame, f"Samples: {len(emb_list)}/{TARGET}", (20, 40),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 255), 2)
        cv2.putText(frame, "Look at camera - good lighting", (20, 80),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.75, (255, 255, 255), 2)
        cv2.putText(frame, "Press ESC to cancel", (20, 120),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.75, (255, 255, 255), 2)

        cv2.imshow("Enrollment", frame)
        key = cv2.waitKey(1) & 0xFF
        if key == 27:
            cap.release()
            cv2.destroyAllWindows()
            print("FAIL cancelled")
            sys.exit(1)

        if len(emb_list) >= TARGET:
            break

        if (time.time() - start) > TIMEOUT:
            cap.release()
            cv2.destroyAllWindows()
            if len(emb_list) >= 3:
                break
            print(f"FAIL timeout_partial_capture samples={len(emb_list)}")
            sys.exit(1)

    cap.release()
    cv2.destroyAllWindows()

    if len(emb_list) < 3:
        print("FAIL no_face_detected")
        sys.exit(1)

    emb_mean = np.mean(np.stack(emb_list, axis=0), axis=0).astype(np.float32)
    emb_mean /= (np.linalg.norm(emb_mean) + 1e-12)
    np.save(emb_path, emb_mean)

    # Ensure preview exists
    if not os.path.exists(preview_path):
        cv2.imwrite(preview_path, cv2.resize(frame, (240, 240)))

    print(f"OK face_path={preview_path} embedding_path={emb_path} samples={len(emb_list)}")
    sys.exit(0)


if __name__ == "__main__":
    main()
