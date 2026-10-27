import concurrent.futures
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import zipfile

tools = Path(__file__).resolve().parent
root = ET.parse(tools / 'repository.xml').getroot()
def child(element, name):
    return next((e for e in element if e.tag.rsplit('}', 1)[-1] == name), None)
def text(element, name):
    found = child(element, name)
    return found.text.strip() if found is not None and found.text else ''
def fetch(package):
    remote = next(e for e in root.iter() if e.tag.rsplit('}', 1)[-1] == 'remotePackage' and e.get('path') == package)
    archives = child(remote, 'archives')
    archive = next(e for e in archives if text(e, 'host-os') in ('', 'linux'))
    complete = child(archive, 'complete')
    name = text(complete, 'url')
    path = tools / 'downloads' / name
    expected = text(complete, 'checksum')
    if not path.exists() or hashlib.sha1(path.read_bytes()).hexdigest() != expected:
        subprocess.run(['curl', '--fail', '--location', '--connect-timeout', '20', '--max-time', '180', '--retry', '1', '--silent', '--show-error', 'https://dl.google.com/android/repository/' + name, '--output', str(path)], check=True)
    actual = hashlib.sha1(path.read_bytes()).hexdigest()
    if actual != expected:
        raise RuntimeError('SDK archive checksum mismatch: ' + name)
    target = tools / 'sdk' / package.replace(';', '-')
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            resolved = (target / info.filename).resolve()
            if not resolved.is_relative_to(target.resolve()):
                raise RuntimeError('Archive path outside SDK directory')
        archive.extractall(target)
    for tool in ('aapt2', 'zipalign', 'apksigner', 'd8'):
        for found in target.rglob(tool):
            found.chmod(0o755)
    print('Ready:', package, flush=True)
    return {'package': package, 'archive': name, 'sha1': actual, 'path': str(target)}

with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    records = list(pool.map(fetch, ['build-tools;35.0.0', 'platforms;android-35']))
(tools / 'sdk-manifest.json').write_text(json.dumps(records, indent=2) + '\n')
