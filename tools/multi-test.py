"""Exercise isolated multi-pet UI, migration and instance settings on a test emulator.

Only the .test application is reset. The installed release's data is left intact.
Build build-probe.py --legacy-seed and build.py --test-variant before running.
"""
from pathlib import Path
import subprocess, time, re, json, os, xml.etree.ElementTree as ET

project = Path(__file__).resolve().parents[1]
adb = os.environ.get('ADB', 'adb')
serial = os.environ.get('ANDROID_SERIAL')
if not serial:
    raise SystemExit('Set ANDROID_SERIAL to a dedicated emulator.')
package = 'io.github.lacania.aemeathpocket.test'
production = 'io.github.lacania.aemeathpocket'
component = package + '/' + production + '.MainActivity'
out = project / 'test-results' / 'multi-pet'
out.mkdir(parents=True, exist_ok=True)
results = []

def call(*args, check=True, binary=False):
    result = subprocess.run([adb, '-s', serial, *map(str,args)], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=40)
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')

def shell(*args, **kwargs): return call('shell', *args, **kwargs)
def start():
    shell('am','start','-n',component); time.sleep(.4)
def home():
    shell('input','keyevent','3');time.sleep(.4)
def tree():
    shell('uiautomator','dump','/sdcard/flying-ui-test.xml')
    return ET.fromstring(shell('cat','/sdcard/flying-ui-test.xml'))
def rect(node): return tuple(map(int,re.findall(r'-?\d+', node.get('bounds',''))))
def tap(node):
    l,t,r,b=rect(node);shell('input','tap',(l+r)//2,(t+b)//2);time.sleep(.2)
def locate(predicate):
    for direction in [1,-1]:
        for attempt in range(8):
            root=tree()
            found=next((n for n in root.iter('node') if predicate(n) and len(rect(n))==4
                        and rect(n)[3]-rect(n)[1]>=30),None)
            if found is not None:return found
            scroll=next((n for n in root.iter('node') if n.get('scrollable')=='true'),None)
            if scroll is None:break
            l,t,r,b=rect(scroll);x=l+(r-l)*3//4;lo=t+(b-t)//4;hi=b-(b-t)//4
            shell('input','swipe',x,hi if direction==1 else lo,x,lo if direction==1 else hi,200)
    raise AssertionError('Control not found; see latest UI dump')
def button(label): tap(locate(lambda n:n.get('text')==label))
def setting(label,value):
    node=locate(lambda n:n.get('text')==label and n.get('checkable')=='true')
    if (node.get('checked')=='true')!=value:tap(node)
def select(name):
    print('SELECT '+name,flush=True)
    tap(locate(lambda n:n.get('content-desc')=='选择要设置的桌宠'))
    root=tree()
    choice=next((n for n in root.iter('node') if n.get('text')==name),None)
    if choice is not None:tap(choice)
    else:
        current=global_prefs()['selected']
        assert pet_prefs(current)['name']==name,[(n.get('text'),n.get('class')) for n in root.iter('node') if n.get('text')]
    locate(lambda n:n.get('text')=='下面只修改：'+name)
def add(role,count=1):
    print('ADD '+role+' '+str(count),flush=True)
    button('添加桌宠');button(role)
    if count!=1:
        number=locate(lambda n:n.get('class')=='android.widget.EditText')
        tap(number);shell('input','keyevent','123');shell('input','keyevent','67')
        shell('input','text',count);shell('input','keyevent','66');time.sleep(.2)
    button('添加')
def preferences(name):
    data=shell('run-as',package,'cat','shared_prefs/'+name+'.xml')
    root=ET.fromstring(data);values={}
    for child in root:
        value=child.text if child.tag=='string' else child.get('value')
        if child.tag=='boolean':value=value=='true'
        elif child.tag in ('int','long'):value=int(value)
        elif child.tag=='float':value=float(value)
        values[child.get('name')]=value
    return values
def global_prefs():return preferences('flights')
def pet_prefs(id):return preferences('pet_instance_'+id)
def ids():return json.loads(global_prefs()['instances'])
def windows():
    dump=shell('dumpsys','window','windows');found={}
    for block in re.split(r'\n  Window #',dump):
        if 'package='+package+' appop=SYSTEM_ALERT_WINDOW' not in block:continue
        title=re.search(r'FlyingPet:([A-Za-z0-9-]+)',block)
        frame=re.search(r'mFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
        if title and frame:found[title[1]]=(tuple(map(int,frame.groups())),block)
    return found
def shot(name):(out/(name+'.png')).write_bytes(call('exec-out','screencap','-p',binary=True))
def passed(name,**details):
    results.append(dict(check=name,passed=True,**details));print('PASS '+name,flush=True)
def check_packing():
    frames=[item[0] for item in windows().values()]
    assert len(frames)==len(ids()),(len(frames),len(ids()))
    for i,(l,t,r,b) in enumerate(frames):
        for a,c,d,e in frames[i+1:]:assert not (l<d and r>a and t<e and b>c),frames
def slider(description,fraction):
    node=locate(lambda n:n.get('content-desc')==description and n.get('class')=='android.widget.SeekBar')
    l,t,r,b=rect(node);shell('input','tap',int(l+(r-l)*fraction),(t+b)//2);time.sleep(.3)

assert package.endswith('.test') and package!=production
shell('am','force-stop',production)
call('uninstall',package,check=False)
call('install',project/'build/legacy-seed/probe.apk')
shell('am','start','-n',package+'/io.github.lacania.seed.SeedActivity');time.sleep(.5)
call('install','-r',project/'dist/Flying-Edelweiss-0.2.0-ui-test.apk')
shell('cmd','appops','set',package,'SYSTEM_ALERT_WINDOW','allow')
try:
    start()
    old=pet_prefs('legacy')
    assert old['size']==156 and abs(old['opacity']-.85)<.001 and old['throw']
    assert abs(old['physics.strength']-1.65)<.001 and abs(old['physics.elasticity']-.63)<.001
    assert len(ids())==1 and len(windows())==1
    passed('legacy-settings-migrate-without-reset')
    add('菲比');phoebe=global_prefs()['selected'];assert pet_prefs(phoebe)['character']=='phoebe'
    setting('自主走动',False)
    select('爱弥斯 1');setting('点击穿透',True)
    slider('这只桌宠的透明度',.35)
    old_alpha=pet_prefs('legacy')['opacity']
    select('菲比 2');setting('点击穿透',False);slider('这只桌宠的透明度',.65)
    phoebe_alpha=pet_prefs(phoebe)['opacity']
    assert abs(old_alpha-phoebe_alpha)>.1
    assert pet_prefs('legacy')['pass'] and not pet_prefs(phoebe).get('pass',False)
    select('爱弥斯 1')
    setting('自主走动',False)
    button('调整抛射参数');slider('抛射力度',.4)
    strength=pet_prefs('legacy')['physics.strength']
    select('菲比 2');button('调整抛射参数');slider('抛射力度',.8)
    assert abs(pet_prefs('legacy')['physics.strength']-strength)<.001
    assert abs(pet_prefs(phoebe)['physics.strength']-strength)>.2
    select('爱弥斯 1');shot('01-select-aemeath')
    passed('aemeath-and-phoebe-have-independent-opacity-touch-and-physics')
    assert all('NOT_TOUCHABLE' in block for _,block in windows().values())
    home();w=windows()
    assert 'NOT_TOUCHABLE' in w['legacy'][1] and 'NOT_TOUCHABLE' not in w[phoebe][1]
    start();assert all('NOT_TOUCHABLE' in block for _,block in windows().values())
    passed('settings-page-touch-through-does-not-overwrite-instance-options')
    shell('am','force-stop',package);start();select('爱弥斯 1')
    assert abs(pet_prefs('legacy')['opacity']-old_alpha)<.001
    assert abs(pet_prefs('legacy')['physics.strength']-strength)<.001
    passed('selected-instance-and-parameters-survive-restart')
    add('爱弥斯');assert len([id for id in ids() if pet_prefs(id)['character']=='aemeath'])==2
    add('守岸人');add('弗洛洛')
    assert len(windows())==5;shot('02-five-pets')
    passed('all-four-characters-and-duplicate-instance-render')
    for value in [False,True]:
        setting('允许桌宠重叠',value);time.sleep(.4);check_packing()
    passed('both-overlap-switch-directions-stop-and-repack-pets')
    add('菲比',5)
    assert len(ids())==10 and len(windows())==10
    add_button=locate(lambda n:n.get('text')=='添加桌宠');assert add_button.get('enabled')=='false'
    passed('ten-instance-limit-and-disabled-add-button')
    button('一键清空桌宠');button('清空全部');time.sleep(.3)
    assert not ids() and not windows()
    shell('am','force-stop',package);start()
    assert not ids() and not windows()
    passed('clear-all-closes-windows-and-stays-empty-after-restart')
except Exception:
    shot('failure')
    (out/'failure-ui.xml').write_text(ET.tostring(tree(),encoding='unicode'),encoding='utf-8')
    raise
finally:
    (out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    shell('am','force-stop',package)
    shell('am','start','-n',production+'/.MainActivity')
print(json.dumps(results,ensure_ascii=False,indent=2))
