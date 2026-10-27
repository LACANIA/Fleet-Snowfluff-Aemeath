"""Check overlay interaction, screen boundaries and user-controlled exit."""
from pathlib import Path
import subprocess,time,re,json,io,os
import xml.etree.ElementTree as ET
from PIL import Image
project=Path(__file__).resolve().parents[1]
version=ET.parse(project/'app/src/main/AndroidManifest.xml').getroot().attrib['{http://schemas.android.com/apk/res/android}versionName']
out=project/'test-results'
out.mkdir(exist_ok=True)
adb=os.environ.get('ADB','adb');serial=os.environ.get('ANDROID_SERIAL');package='io.github.lacania.aemeathpocket'
if not serial:raise SystemExit('Set ANDROID_SERIAL to the dedicated test emulator. ADB can override the adb path.')
results=[]
def call(*args,binary=False):
    process=subprocess.run([adb,'-s',serial,*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=35)
    if process.returncode:raise RuntimeError(process.stderr.decode('utf-8',errors='replace'))
    return process.stdout if binary else process.stdout.decode('utf-8',errors='replace')
def shell(*args):return call('shell',*args)
def screen(name=None):
    data=call('exec-out','screencap','-p',binary=True)
    if name:(out/(name+'.png')).write_bytes(data)
    return Image.open(io.BytesIO(data)).size
def tree():
    shell('uiautomator','dump','/sdcard/aemeath-personal-test.xml')
    return ET.fromstring(shell('cat','/sdcard/aemeath-personal-test.xml'))
def tap_label(label,scroll=False):
    for attempt in range(8 if scroll else 1):
        root=tree();node=next((n for n in root.iter('node') if n.get('text')==label),None)
        if node is not None:
            x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
            if x2>x1 and y2>y1:
                shell('input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.35);return
        if scroll:
            listing=next((n for n in root.iter('node') if n.get('scrollable')=='true'),None)
            if listing is None:break
            x1,y1,x2,y2=map(int,re.findall(r'\d+',listing.get('bounds')))
            shell('input','swipe',str((x1+x2)//2),str(y2-30),str((x1+x2)//2),str(y1+30),'220')
    raise AssertionError('Missing menu control: '+label)
def bounds():
    dump=shell('dumpsys','window','windows')
    blocks=re.split(r'\n  Window #',dump)
    candidates=[]
    for block in blocks:
        if 'package='+package+' appop=SYSTEM_ALERT_WINDOW' not in block or 'ty=APPLICATION_OVERLAY' not in block:continue
        match=re.search(r'mFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
        if match:
            rectangle=tuple(map(int,match.groups()));candidates.append((rectangle,block))
    if not candidates:raise AssertionError('Pet overlay window missing')
    return min(candidates,key=lambda item:(item[0][2]-item[0][0])*(item[0][3]-item[0][1]))
def check_bounds(label):
    rectangle,block=bounds();width,height=screen();left,top,right,bottom=rectangle
    assert 0<=left<right<=width and 0<=top<bottom<=height,(label,rectangle,(width,height))
    results.append(dict(check=label,bounds=rectangle,screen=[width,height],passed=True))
    return rectangle
def open_menu():
    left,top,right,bottom=bounds()[0];x,y=str((left+right)//2),str((top+bottom)//2)
    shell('input','swipe',x,y,x,y,'850');time.sleep(.3)
    assert any(n.get('text')=='爱弥斯的小动作' for n in tree().iter('node')),'Stationary hold did not open menu'

shell('am','start','-n',package+'/.MainActivity');time.sleep(.4);tap_label('让爱弥斯出来');time.sleep(.4)
shell('input','keyevent','3');time.sleep(.4)
open_menu();screen('04-pet-action-menu')
try:tap_label('暂停走动')
except AssertionError:tap_label('×')
for label,target in [('left-top',(-300,-300)),('right-top',(5000,-300)),('right-bottom',(5000,5000)),('left-bottom',(-300,5000))]:
    left,top,right,bottom=bounds()[0]
    shell('input','swipe',str((left+right)//2),str((top+bottom)//2),str(target[0]),str(target[1]),'350')
    time.sleep(.15);check_bounds(label)
screen('05-drag-clamped-at-edge')
open_menu();tap_label('偷偷打盹',scroll=True);time.sleep(.7);screen('06-local-nap-animation')
open_menu();tap_label('开心蹦跳');time.sleep(.4)
original_rotation=shell('cmd','settings','get','system','user_rotation').strip()
original_accelerometer=shell('cmd','settings','get','system','accelerometer_rotation').strip()
try:
    shell('cmd','settings','put','system','accelerometer_rotation','0')
    shell('am','start','-n',package+'/.MainActivity');time.sleep(.4)
    for rotation in ['0','1']:
        shell('cmd','settings','put','system','user_rotation',rotation);time.sleep(1)
        check_bounds('rotation-'+rotation);screen('07-rotation-'+rotation)
finally:
    if original_rotation not in ('','null'):shell('cmd','settings','put','system','user_rotation',original_rotation)
    if original_accelerometer not in ('','null'):shell('cmd','settings','put','system','accelerometer_rotation',original_accelerometer)
shell('input','keyevent','3');time.sleep(.4)
shell('input','keyevent','223');time.sleep(.4)
power=shell('dumpsys','power')
if 'mWakefulness=Awake' in power:
    results.append(dict(check='screen-off-hides-and-pauses-pet',passed=None,reason='Local MuMu emulator remains Awake after KEYCODE_SLEEP; verify on a physical phone.'))
else:
    assert 'mViewVisibility=0x4' in bounds()[1],'Pet must hide while screen is off'
    results.append(dict(check='screen-off-hides-and-pauses-pet',passed=True))
shell('input','keyevent','224');time.sleep(.5);shell('wm','dismiss-keyguard');time.sleep(.4)
check_bounds('resume-after-screen-on')
open_menu();tap_label('退出');time.sleep(.6)
assert 'isForeground=true' not in shell('dumpsys','activity','services',package),'Exit left the foreground service running'
results.append(dict(check='pet-menu-exit-clears-service',passed=True))
(out/'interaction-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(results,ensure_ascii=False,indent=2))
