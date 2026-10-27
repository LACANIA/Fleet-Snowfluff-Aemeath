"""Build and sign a personal-test APK with the isolated official Android tools."""
from pathlib import Path
import os
import subprocess
import zipfile
import json
import hashlib
import xml.etree.ElementTree as ET
import argparse

parser=argparse.ArgumentParser();parser.add_argument('--test-variant',action='store_true');args=parser.parse_args()

project=Path(__file__).resolve().parents[1]
tools=project/'tools'
jdk_root=tools/'jdk'
for path in jdk_root.rglob('*'):
    if path.is_symlink():
        target=os.readlink(path)
        candidate=jdk_root/target.lstrip('/')
        if target.startswith('/') and candidate.exists():
            path.unlink()
            path.symlink_to(os.path.relpath(candidate,path.parent))
java_home=Path(os.environ.get('JAVA_HOME',jdk_root/'usr/lib/jvm/java-17-openjdk-amd64'))
sdk_home=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if sdk_home:
    build_tools=Path(sdk_home)/'build-tools/35.0.0'
    android_jar=Path(sdk_home)/'platforms/android-35/android.jar'
else:
    candidates=list((tools/'sdk/build-tools-35.0.0').glob('*/aapt2'))
    jars=list((tools/'sdk/platforms-android-35').rglob('android.jar'))
    if not candidates or not jars:
        raise SystemExit('Set JAVA_HOME and ANDROID_HOME; install Android Platform 35 and Build Tools 35.0.0. See README.md.')
    build_tools=candidates[0].parent
    android_jar=jars[0]
if not (java_home/'bin/javac').is_file() or not android_jar.is_file() or not (build_tools/'aapt2').is_file():
    raise SystemExit('Missing JDK 17 or Android SDK components. This build script runs on Linux / WSL; see README.md.')
build=project/'build'
if args.test_variant:build=build/'ui-test-variant'
dist=project/'dist'
for folder in ['generated','classes','dex']:(build/folder).mkdir(parents=True,exist_ok=True)
dist.mkdir(exist_ok=True)
env=dict(os.environ,JAVA_HOME=str(java_home),PATH=str(java_home/'bin')+os.pathsep+os.environ['PATH'])
def run(*args):
    subprocess.run([str(a) for a in args],cwd=project,env=env,check=True)
main=project/'app/src/main'
manifest=main/'AndroidManifest.xml';extra=[]
if args.test_variant:
    tree=ET.parse(manifest);root=tree.getroot();original=root.get('package');ns='{http://schemas.android.com/apk/res/android}'
    for node in list(root.find('application')):
        name=node.get(ns+'name','')
        if name.startswith('.'):node.set(ns+'name',original+name)
    root.find('application').set(ns+'label','飞行雪绒 测试')
    root.find('application').set(ns+'debuggable','true')
    manifest=build/'test-manifest.xml';tree.write(manifest,encoding='utf-8',xml_declaration=True)
    extra=['--rename-manifest-package',original+'.test','--custom-package',original]
run(build_tools/'aapt2','compile','--dir',main/'res','-o',build/'resources.zip')
run(build_tools/'aapt2','link','-o',build/'unsigned.apk','--manifest',manifest,'-I',android_jar,'--java',build/'generated','-A',main/'assets',build/'resources.zip',*extra)
sources=list((main/'java').rglob('*.java'))+list((build/'generated').rglob('*.java'))
run(java_home/'bin/javac','-encoding','UTF-8','--release','8','-classpath',android_jar,'-d',build/'classes',*sources)
classes=list((build/'classes').rglob('*.class'))
run(java_home/'bin/java','-cp',build_tools/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',android_jar,'--output',build/'dex',*classes)
with zipfile.ZipFile(build/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as archive:
    for dex in (build/'dex').glob('*.dex'):archive.write(dex,dex.name)
run(build_tools/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk')
key=tools/'personal-test.keystore'
if not key.exists():
    run(java_home/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-alias','personal-test','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Aemeath Personal Test')
version=ET.parse(main/'AndroidManifest.xml').getroot().attrib['{http://schemas.android.com/apk/res/android}versionName']
apk=dist/('Fleet-Snowfluff-'+version+('-ui-test.apk' if args.test_variant else '-test.apk'))
run(java_home/'bin/java','-jar',build_tools/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,build/'aligned.apk')
run(java_home/'bin/java','-jar',build_tools/'lib/apksigner.jar','verify','--verbose',apk)
run(build_tools/'zipalign','-c','4',apk)
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
    names=archive.namelist()
    assert len(names)==len(set(names)),'Duplicate APK archive entries'
    assert 'classes.dex' in names and 'assets/animations.json' in names
record=dict(apk=str(apk),sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),bytes=apk.stat().st_size,minSdk=26,targetSdk=35,signatureVerified=True,alignmentVerified=True)
(dist/('build-info-ui-test.json' if args.test_variant else 'build-info.json')).write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps(record,indent=2))
