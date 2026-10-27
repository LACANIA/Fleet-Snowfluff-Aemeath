"""Verify a visible pet forwards taps to a separate Android UID's button."""
from pathlib import Path
source=Path(__file__).with_name('interaction-test.py')
exec(compile(source.read_text(encoding='utf-8').split("\nshell('am','start','-n',package")[0],str(source),'exec'))
out=project/'test-results'/('v'+version);out.mkdir(parents=True,exist_ok=True)
probe='io.github.lacania.overlayprobe'

def uid(name):return re.search(r'userId=(\d+)',shell('dumpsys','package',name)).group(1)
assert uid(probe)!=uid(package),'The target must be a separate Android UID'
shell('am','force-stop',package);shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
for label in ['点击穿透','拖拽抛射','自主走动']:
    node=next(n for n in tree().iter('node') if n.get('text')==label)
    if node.get('checked')=='true':tap_label(label)
tap_label('让爱弥斯出来')
shell('am','force-stop',probe);shell('am','start','-n',probe+'/.ProbeActivity');time.sleep(.3)
node=next(n for n in tree().iter('node') if n.get('content-desc')=='Overlay probe target')
button_bounds=tuple(map(int,re.findall(r'\d+',node.get('bounds'))))
left,top,right,bottom=bounds()[0]
target=((left+right)//2,(top+bottom)//2)
assert button_bounds[0]<=target[0]<=button_bounds[2] and button_bounds[1]<=target[1]<=button_bounds[3]
shell('am','start','-n',package+'/.MainActivity');time.sleep(.3);tap_label('点击穿透')
shell('am','start','-n',probe+'/.ProbeActivity');time.sleep(.3)
rectangle,block=bounds();assert 'NOT_TOUCHABLE' in block and 'alpha=0.75' in block
assert 'mHasSurface=true' in block and 'shown=true' in block,'Pet must actually be visible above the target app'
assert rectangle[0]<=target[0]<=rectangle[2] and rectangle[1]<=target[1]<=rectangle[3]
screen('03-visible-pet-over-separate-app')
shell('input','tap',str(target[0]),str(target[1]));time.sleep(.2)
assert any(n.get('text','').upper()=='TAP COUNT: 1' for n in tree().iter('node'))
results.append(dict(check='visible-pet-forwards-tap-to-separate-uid',passed=True,petBounds=rectangle,tap=target,alpha=.75))
shell('am','start','-n',package+'/.MainActivity');time.sleep(.3);tap_label('点击穿透')
assert 'NOT_TOUCHABLE' not in bounds()[1]
results.append(dict(check='app-switch-restores-pet-touch',passed=True))
results.append(dict(check='notification-restores-pet-touch',passed=None,reason='MuMu does not expand its notification shade; verify this action on a physical phone.'))
tap_label('回到初始位置',scroll=True);shell('input','keyevent','3');time.sleep(.3)
check_bounds('ready-for-user');screen('05-ready-for-user')
(out/'click-through-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(results,ensure_ascii=True,indent=2))
