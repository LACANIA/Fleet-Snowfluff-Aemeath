"""Build a separate-UID touch-test fixture; never include it in the pet's release."""
from pathlib import Path
import os,subprocess,zipfile
project=Path(__file__).resolve().parents[1];tools=project/'tools'
java=Path(os.environ.get('JAVA_HOME',tools/'jdk/usr/lib/jvm/java-17-openjdk-amd64'))
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
bt=Path(sdk)/'build-tools/35.0.0' if sdk else next((tools/'sdk/build-tools-35.0.0').glob('*/aapt2')).parent
jar=Path(sdk)/'platforms/android-35/android.jar' if sdk else next((tools/'sdk/platforms-android-35').rglob('android.jar'))
out=project/'build/overlay-probe';out.mkdir(parents=True,exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
def run(*args):subprocess.run(list(map(str,args)),check=True)
source=project/'tests/overlay-probe'
run(bt/'aapt2','link','--manifest',source/'AndroidManifest.xml','-I',jar,'-o',out/'unsigned.apk')
run(java/'bin/javac','--release','8','-encoding','UTF-8','-classpath',jar,'-d',classes,source/'ProbeActivity.java')
run(java/'bin/java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',jar,'--output',dex,*classes.rglob('*.class'))
with zipfile.ZipFile(out/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
run(bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
key=tools/'personal-test.keystore'
if not key.is_file():raise SystemExit('Build the pet first to create the local test key.')
run(java/'bin/java','-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--out',out/'probe.apk',out/'aligned.apk')
run(java/'bin/java','-jar',bt/'lib/apksigner.jar','verify',out/'probe.apk')
print(out/'probe.apk')
