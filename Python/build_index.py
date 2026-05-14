import os, sys, json
import numpy as np

# quiet
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

try:
    import faiss
except Exception:
    faiss = None


def load_index_txt(path):
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


def main():
    if faiss is None:
        print("FAIL faiss_missing")
        sys.exit(1)

    if len(sys.argv) < 2:
        print("FAIL missing_users_index")
        sys.exit(2)

    users_index_txt = sys.argv[1]
    rows = load_index_txt(users_index_txt)
    if not rows:
        print("FAIL no_enrolled_users")
        sys.exit(1)

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
            # must be normalized already, but normalize again safely
            v /= (np.linalg.norm(v) + 1e-12)
            vecs.append(v)
            emails.append(email)
        except Exception:
            skipped += 1

    if not vecs:
        print(f"FAIL no_valid_face_images skipped={skipped}")
        sys.exit(1)

    X = np.stack(vecs, axis=0).astype(np.float32)  # (N, D)
    d = X.shape[1]

    # cosine similarity via inner product on L2-normalized vectors
    index = faiss.IndexFlatIP(d)
    index.add(X)

    os.makedirs("faces", exist_ok=True)
    out_index = os.path.abspath(os.path.join("faces", "faiss.index"))
    out_map = os.path.abspath(os.path.join("faces", "faiss_map.json"))

    faiss.write_index(index, out_index)
    with open(out_map, "w", encoding="utf-8") as f:
        json.dump({"emails": emails, "dim": d}, f)

    print(f"OK built_index={out_index} map={out_map} users={len(emails)} skipped={skipped}")
    sys.exit(0)


if __name__ == "__main__":
    main()