#!/usr/bin/env python3
"""Download an inspected revision; keep diagnostics off --print-path stdout."""
import argparse
import hashlib
import json
from pathlib import Path
from huggingface_hub import hf_hub_download

def main():
    models = json.loads(Path(__file__).with_name("models.json").read_text())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-id", default=models[0]["repo"], choices=[m["repo"] for m in models])
    parser.add_argument("--output-dir", type=Path, default=Path("models"))
    parser.add_argument("--print-path", action="store_true")
    args = parser.parse_args()
    m = next(m for m in models if m["repo"] == args.repo_id)
    source = Path(hf_hub_download(repo_id=m["repo"], filename=m["file"], revision=m["revision"]))
    data = source.read_bytes()
    if hashlib.sha256(data).hexdigest() != m["sha256"]:
        raise SystemExit("Model checksum mismatch; refusing to package this file")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    dest = args.output_dir / (m["id"] + ".pte")
    temp = dest.with_suffix(".tmp")
    temp.write_bytes(data)
    temp.replace(dest)
    print(dest.resolve() if args.print_path else "Downloaded " + str(dest.resolve()))

if __name__ == "__main__":
    main()
