"""Exercise this app only on the explicitly selected local Android emulator."""
from pathlib import Path
import subprocess
import os
import time
import xml.etree.ElementTree as ET
import json
import re

project=Path(__file__).resolve().parents[1]
out=project/'test-results'
out.mkdir(exist_ok=True)
adb=os.environ.get('ADB','adb')
serial=os.environ.get('ANDROID_SERIAL')
if not serial:raise SystemExit('Set ANDROID_SERIAL to a dedicated test emulator.')
package='io.github.lacania.aemeathpocket'
def call(*args, binary=False):
    result=subprocess.run([adb,'-s',serial,*args],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=35)
    return result.stdout if binary else result.stdout.decode('utf-8',errors='replace')
def shell(*args):return call('shell',*args)
def tree():
    shell('uiautomator','dump','/sdcard/aemeath-personal-test.xml')
    return ET.fromstring(shell('cat','/sdcard/aemeath-personal-test.xml'))
def tap(text):
    root=tree()
    node=next((n for n in root.iter('node') if n.get('text')==text),None)
    if node is None:raise AssertionError('Missing control: '+text)
    numbers=[int(n) for n in re.findall(r'\d+',node.get('bounds'))]
    shell('input','tap',str((numbers[0]+numbers[2])//2),str((numbers[1]+numbers[3])//2))
    time.sleep(.4)
def screenshot(name):
    (out/(name+'.png')).write_bytes(call('exec-out','screencap','-p',binary=True))
def active():
    result=shell('dumpsys','activity','services',package)
    return 'isForeground=true' in result

shell('am','start','-n',package+'/.MainActivity')
time.sleep(1)
screenshot('01-settings-before-permission')
shell('cmd','appops','set',package,'SYSTEM_ALERT_WINDOW','allow')
tap('让爱弥斯出来')
time.sleep(1)
assert active(),'Foreground pet service did not start'
screenshot('02-settings-with-pet')
shell('input','keyevent','4')
time.sleep(.6)
assert active(),'Closing the settings Activity stopped the pet'
screenshot('03-pet-after-settings-closed')
(out/'service-after-close.txt').write_text(shell('dumpsys','activity','services',package),encoding='utf-8')
print('Install, launch, overlay permission and close-settings persistence: PASS')
(out/'first-smoke.json').write_text(json.dumps(dict(api=int(shell('getprop','ro.build.version.sdk').strip()),foregroundServiceAfterActivityClosed=True),indent=2)+'\n')
