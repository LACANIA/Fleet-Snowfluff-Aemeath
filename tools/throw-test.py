"""Verify optional drag-release inertia on the explicitly selected emulator."""
from pathlib import Path
source=Path(__file__).with_name('interaction-test.py')
exec(compile(source.read_text(encoding='utf-8').split("\nshell('am','start','-n',package")[0],str(source),'exec'))
out=project/'test-results'/('v'+version);out.mkdir(parents=True,exist_ok=True)

def switch(label,wanted):
    root=tree();node=next((n for n in root.iter('node') if n.get('text')==label),None)
    if node is None:
        tap_label(label,scroll=True)
        node=next(n for n in tree().iter('node') if n.get('text')==label)
    if (node.get('checked')=='true')!=wanted:tap_label(label)

def home():shell('input','keyevent','3');time.sleep(.3)
def center():
    l,t,r,b=bounds()[0];return (l+r)//2,(t+b)//2
def drag(dx,dy,duration):
    x,y=center();shell('input','swipe',str(x),str(y),str(x+dx),str(y+dy),str(duration));return x+dx,y+dy
def stable(label):
    first=bounds()[0];time.sleep(.5);second=bounds()[0]
    assert first==second,(label,first,second)
    results.append(dict(check=label,passed=True,bounds=first))
def samples(seconds=2):
    end=time.monotonic()+seconds;rectangles=[];width,height=screen()
    while time.monotonic()<end:
        rectangle=bounds()[0];l,t,r,b=rectangle
        assert 0<=l<r<=width and 0<=t<b<=height,rectangle
        rectangles.append(rectangle);time.sleep(.025)
    return rectangles

shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
initial=next(n for n in tree().iter('node') if n.get('text')=='拖拽抛射')
results.append(dict(check='initial-throw-preference',enabled=initial.get('checked')=='true'))
switch('拖拽抛射',False)
switch('自主走动',False);switch('点击穿透',False);tap_label('让爱弥斯出来');home()
drag(-200,-100,180);stable('disabled-switch-keeps-pet-at-release-position')

shell('am','start','-n',package+'/.MainActivity');time.sleep(.3);switch('拖拽抛射',True);home()
# A slow positioning gesture stays below the fling threshold.
x,y=center();width,height=screen()
shell('input','swipe',str(x),str(y),str(width//2),str(height//3),'2200');stable('slow-drag-does-not-start-throw')
drag(430,-100,120);path=samples(2.5)
assert len(set(path))>=3,path
assert any(path[i][1]>path[i-1][1] for i in range(1,len(path))),'Gravity must change vertical position'
assert any(path[i][0]<path[i-1][0] for i in range(1,len(path))),'Right edge must bounce the pet back'
results.append(dict(check='fast-drag-throws-bounces-and-stays-inside-screen',passed=True,sampledBounds=path))
time.sleep(13);stable('throw-damping-finishes')

drag(380,-180,120)
assert len(set(samples(.2)))>=2,'Interruption must be tested during an active throw'
x,y=center()
shell('input','motionevent','DOWN',str(x),str(y));time.sleep(.2)
stopped=bounds()[0];time.sleep(.3);assert stopped==bounds()[0]
shell('input','motionevent','UP',str(x),str(y));stable('touch-interrupts-throw')

drag(-350,-180,120)
assert len(set(samples(.2)))>=2,'Switch cancellation must be tested during an active throw'
shell('am','start','-n',package+'/.MainActivity');time.sleep(.2);switch('拖拽抛射',False);home()
stable('disabling-switch-cancels-current-throw')
# Persist the preference over an application process restart.
shell('am','force-stop',package);shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
node=next(n for n in tree().iter('node') if n.get('text')=='拖拽抛射')
assert node.get('checked')=='false';tap_label('让爱弥斯出来');home()
results.append(dict(check='throw-preference-persists-after-restart',passed=True))
(out/'throw-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(results,ensure_ascii=True,indent=2))
