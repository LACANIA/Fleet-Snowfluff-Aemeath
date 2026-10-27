"""Run the Android-independent position and gesture tests with JDK 17."""
import os
from pathlib import Path
import shutil
import subprocess

project=Path(__file__).resolve().parents[1]
local=project/'tools/jdk/usr/lib/jvm/java-17-openjdk-amd64'
java_home=Path(os.environ.get('JAVA_HOME',local))
suffix='.exe' if os.name=='nt' else ''

def executable(name):
    path=java_home/('bin/'+name+suffix)
    found=str(path) if path.is_file() else shutil.which(name)
    if not found:raise SystemExit('Set JAVA_HOME to JDK 17, or place java and javac on PATH.')
    return found

out=project/'build/test-classes';out.mkdir(parents=True,exist_ok=True)
sources=[project/'app/src/main/java/io/github/lacania/aemeathpocket'/name for name in ['Motion.java','PetGesture.java','ThrowMotion.java','ThrowTrail.java','PhysicsOptions.java','SpeechPlacement.java']]
sources+=list((project/'tests').glob('*.java'))
subprocess.run([executable('javac'),'-encoding','UTF-8','--release','8','-d',str(out),*map(str,sources)],check=True)
for name in ['MotionTest','GestureTest','ThrowMotionTest','ThrowTrailTest','PhysicsOptionsTest','SpeechPlacementTest']:
    subprocess.run([executable('java'),'-cp',str(out),name],check=True)
