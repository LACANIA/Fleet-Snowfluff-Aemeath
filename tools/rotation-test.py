from pathlib import Path
import subprocess,time,io,re,json,os
import xml.etree.ElementTree as ET
from PIL import Image
project=Path(__file__).resolve().parents[1]
out=project/'test-results';package='io.github.lacania.aemeathpocket'
out.mkdir(exist_ok=True)
adb=os.environ.get('ADB','adb')
manager=os.environ.get('MUMU_MANAGER')
serial=os.environ.get('ANDROID_SERIAL')
if not manager or not serial:raise SystemExit('Set ANDROID_SERIAL and MUMU_MANAGER for a dedicated MuMu test instance.')
instance=os.environ.get('MUMU_INSTANCE','0')
def call(*args):return subprocess.check_output([adb,'-s',serial,*args],timeout=30)
def shell(*args):return call('shell',*args).decode('utf-8',errors='replace')
shell('am','start','-n',package+'/.MainActivity');time.sleep(.5)
shell('uiautomator','dump','/sdcard/aemeath-personal-test.xml')
tree=ET.fromstring(shell('cat','/sdcard/aemeath-personal-test.xml'))
node=next(n for n in tree.iter('node') if n.get('text')=='让爱弥斯出来')
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')));shell('input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)
results=[]
try:
    for rotation in ('0','1'):
        if rotation=='1':subprocess.check_output([manager,'control','-v',instance,'tool','func','--name','rotate'],timeout=30)
        time.sleep(1)
        image=call('exec-out','screencap','-p');(out/('08-actual-rotation-'+rotation+'.png')).write_bytes(image)
        size=Image.open(io.BytesIO(image)).size
        dump=shell('dumpsys','window','windows')
        block=next(block for block in re.split(r'\n  Window #',dump) if 'package='+package+' appop=SYSTEM_ALERT_WINDOW' in block and 'ty=APPLICATION_OVERLAY' in block)
        match=re.search(r'mFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
        left,top,right,bottom=map(int,match.groups())
        assert 0<=left<right<=size[0] and 0<=top<bottom<=size[1]
        results.append(dict(rotation=int(rotation),screen=size,bounds=[left,top,right,bottom],insideScreen=True))
finally:
    subprocess.check_output([manager,'control','-v',instance,'tool','func','--name','rotate'],timeout=30)
actual=len({tuple(item['screen']) for item in results})==2
record=dict(actualOrientationChanged=actual,positions=results)
(out/'rotation-results.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps(record,indent=2))
if not actual:raise AssertionError('Emulator did not actually rotate')
