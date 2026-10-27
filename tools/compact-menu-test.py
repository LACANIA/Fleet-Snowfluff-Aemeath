"""Exercise stationary hold recognition and compact overlay placement."""
from pathlib import Path
source=Path(__file__).with_name('interaction-test.py')
exec(compile(source.read_text(encoding='utf-8').split("\nshell('am','start','-n',package")[0],str(source),'exec'))
out=project/'test-results'/('v'+version);out.mkdir(parents=True,exist_ok=True)
def has_menu():return any(n.get('text')=='爱弥斯的小动作' for n in tree().iter('node'))
def center():
    left,top,right,bottom=bounds()[0]
    return (left+right)//2,(top+bottom)//2
def hold(duration=850):
    x,y=center();shell('input','swipe',str(x),str(y),str(x),str(y),str(duration));time.sleep(.2)
def menu_rect():
    candidates=[]
    for block in re.split(r'\n  Window #',shell('dumpsys','window','windows')):
        if 'package='+package+' appop=SYSTEM_ALERT_WINDOW' not in block or 'ty=APPLICATION_OVERLAY' not in block:continue
        match=re.search(r'mFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
        if match:candidates.append(tuple(map(int,match.groups())))
    assert len(candidates)==2,'Expected pet and compact panel only'
    return max(candidates,key=lambda r:(r[2]-r[0])*(r[3]-r[1]))
def checkpoint(name,**data):results.append(dict(check=name,passed=True,**data))

shell('am','start','-n',package+'/.MainActivity');time.sleep(.5)
for label in ['点击穿透','自主走动']:
    control=next(n for n in tree().iter('node') if n.get('text')==label)
    if control.get('checked')=='true':tap_label(label)
tap_label('让爱弥斯出来');time.sleep(.3);shell('input','keyevent','3');time.sleep(.4)
x,y=center();shell('input','tap',str(x),str(y));time.sleep(.2)
assert not has_menu();checkpoint('short-tap-does-not-open-menu')
hold(300);assert not has_menu();checkpoint('short-hold-does-not-open-menu')
hold();assert has_menu();checkpoint('stationary-hold-opens-menu')
screen('01-compact-menu')
rectangle=menu_rect();width,height=screen()
assert rectangle[2]-rectangle[0]<width*.85 and rectangle[3]-rectangle[1]<height*.75,(rectangle,(width,height))
checkpoint('menu-is-compact',bounds=rectangle,screen=[width,height])
for label in ['设置','退出','×']:assert any(n.get('text')==label for n in tree().iter('node')),label
tap_label('×');assert not has_menu()

# Hold past the menu duration, then drag; only the eventual release decides.
x,y=center();target=x-140 if x>width//2 else x+140
shell('input','motionevent','DOWN',str(x),str(y));time.sleep(.8)
assert not has_menu(),'Menu must wait for release so a later drag can be recognised'
shell('input','motionevent','MOVE',str(target),str(y));shell('input','motionevent','UP',str(target),str(y));time.sleep(.2)
assert not has_menu();assert center()[0]!=x;checkpoint('long-hold-followed-by-drag-does-not-open-menu')
x,y=center();target=x-120 if x>width//2 else x+120
shell('input','motionevent','DOWN',str(x),str(y));time.sleep(.7)
shell('input','motionevent','MOVE',str(target),str(y));shell('input','motionevent','MOVE',str(x),str(y));shell('input','motionevent','UP',str(x),str(y));time.sleep(.2)
assert not has_menu();checkpoint('drag-returning-to-start-does-not-open-menu')

for label,target in [('left-top',(-300,-300)),('right-top',(5000,-300)),('right-bottom',(5000,5000)),('left-bottom',(-300,5000))]:
    x,y=center();shell('input','swipe',str(x),str(y),str(target[0]),str(target[1]),'350');time.sleep(.15)
    assert not has_menu();hold();assert has_menu()
    rectangle=menu_rect();width,height=screen()
    assert 0<=rectangle[0]<rectangle[2]<=width and 0<=rectangle[1]<rectangle[3]<=height
    checkpoint('panel-stays-on-screen-at-'+label,bounds=rectangle)
    if label=='left-bottom':screen('02-panel-at-screen-edge')
    tap_label('×')

hold();tap_label('偷偷打盹',scroll=True);assert not has_menu();checkpoint('scrollable-actions-work')
hold();tap_label('退出');time.sleep(.4)
assert 'isForeground=true' not in shell('dumpsys','activity','services',package);checkpoint('fixed-exit-button-stops-pet')
(out/'compact-menu-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(results,ensure_ascii=False,indent=2))
