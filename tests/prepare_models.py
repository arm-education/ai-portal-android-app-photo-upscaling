"""Stage checksum-verified weights in the test APK only, never the app APK."""
import hashlib
import json
import shutil
from pathlib import Path
from huggingface_hub import hf_hub_download
root=Path(__file__).resolve().parents[1]
dest=root/'app/src/androidTest/assets'
dest.mkdir(parents=True,exist_ok=True)
for m in json.loads((root/'models.json').read_text()):
    source=Path(hf_hub_download(m['repo'],m['file'],revision=m['revision']))
    if hashlib.sha256(source.read_bytes()).hexdigest()!=m['sha256']:
        raise SystemExit('Checksum mismatch: '+m['id'])
    shutil.copyfile(source,dest/(m['id']+'.pte'))
print('Prepared all three test-only models')
