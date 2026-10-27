"""Exercise four sliders, persistence, reset and rotation on a dedicated MuMu instance."""
from pathlib import Path
source=Path(__file__).with_name('interaction-test.py')
exec(compile(source.read_text(encoding='utf-8').split("\nshell('am','start','-n',package")[0],str(source),'exec'))
out=project/'test-results'/('v'+version);out.mkdir(parents=True,exist_ok=True)
manager=os.environ.get('MUMU_MANAGER')
if not manager:raise SystemExit('Set MUMU_MANAGER for this rotation test.')
instance=os.environ.get('MUMU_INSTANCE','0')

def locate(predicate):
    for direction in [1,-1]:
        for attempt in range(7):
            root=tree()
            node=next((n for n in root.iter('node') if predicate(n) and rect(n)[3]-rect(n)[1]>=35),None)
            if node is not None:return node
            listing=next((n for n in root.iter('node') if n.get('scrollable')=='true'),None)
            if listing is None:break
            l,t,r,b=rect(listing);x=(l+r)//2;lo=t+(b-t)//4;hi=b-(b-t)//4
            shell('input','swipe',str(x),str(hi if direction==1 else lo),str(x),str(lo if direction==1 else hi),'240')
    raise AssertionError('Control not found')
def rect(node):return tuple(map(int,re.findall(r'\d+',node.get('bounds'))))
def tap_node(node):
    l,t,r,b=rect(node);shell('input','tap',str((l+r)//2),str((t+b)//2));time.sleep(.2)
def button(label):tap_node(locate(lambda n:n.get('text')==label))
def caption(prefix):return locate(lambda n:n.get('text','').startswith(prefix+' · ')).get('text')
def setting(label,value):
    node=locate(lambda n:n.get('text')==label)
    if (node.get('checked')=='true')!=value:tap_node(node)
def rotate():
    subprocess.check_output([manager,'control','-v',instance,'tool','func','--name','rotate'],timeout=30);time.sleep(.7)
def center():
    l,t,r,b=bounds()[0];return (l+r)//2,(t+b)//2
def swipe_to(x,y,duration):
    startX,startY=center();shell('input','swipe',str(startX),str(startY),str(x),str(y),str(duration))
def current_bounds():
    rectangle=bounds()[0];w,h=screen();l,t,r,b=rectangle
    assert 0<=l<r<=w and 0<=t<b<=h,(rectangle,(w,h))
    return rectangle

shell('am','start','-n',package+'/.MainActivity');time.sleep(.4)
button('收起桌宠');setting('点击穿透',False);setting('拖拽抛射',False);setting('自主走动',False)
button('调整抛射参数')
titles=['抛射力度','反弹弹性','重力','地面摩擦'];changed={}
for title in titles:
    node=locate(lambda n:n.get('content-desc')==title and n.get('class')=='android.widget.SeekBar')
    l,t,r,b=rect(node);y=(t+b)//2
    shell('input','swipe',str(l+(r-l)//3),str(y),str(l+(r-l)*3//4),str(y),'220')
    changed[title]=caption(title)
screen('06-four-physics-controls')
shell('am','force-stop',package);shell('am','start','-n',package+'/.MainActivity');time.sleep(.4);button('调整抛射参数')
for title in titles:assert caption(title)==changed[title],(title,changed,caption(title))
results.append(dict(check='four-sliders-change-and-persist',passed=True,values=changed))
button('恢复默认抛射参数')
defaults={'抛射力度':'1.00×','反弹弹性':'78%','重力':'1400 dp/s²','地面摩擦':'2.5 /s'}
for title,value in defaults.items():assert caption(title)==title+' · '+value
results.append(dict(check='one-button-restores-all-defaults',passed=True))
original_size=screen()
try:
    rotate();assert screen()!=original_size,'Actual screen dimensions must change'
    assert caption('重力')=='重力 · 1400 dp/s²'
    results.append(dict(check='expanded-physics-panel-survives-rotation',passed=True))
finally:rotate()
button('收起抛射参数');setting('拖拽抛射',True);button('让爱弥斯出来');shell('input','keyevent','3');time.sleep(.3)
w,h=screen();swipe_to(w//2,h//3,2500);x,y=center();swipe_to(x+420,y-100,120)
before=bounds()[0];time.sleep(.12);assert before!=bounds()[0],'Rotate while the pet is actually flying'
try:
    rotate();rotated_size=screen();assert rotated_size!=original_size
    stopped=current_bounds();time.sleep(.35);assert current_bounds()==stopped,'Rotation must stop the old trajectory'
    results.append(dict(check='rotation-cancels-old-flight-and-rebounds-window',passed=True,screen=rotated_size,bounds=stopped))
    w,h=rotated_size;swipe_to(w//2,h//2,2500);x,y=center();swipe_to(x+400,y-80,120)
    end=time.monotonic()+4;lowest=0
    while time.monotonic()<end:
        rectangle=current_bounds();lowest=max(lowest,rectangle[3]);time.sleep(.05)
    assert lowest>h*.85,(lowest,h)
    results.append(dict(check='new-flight-falls-toward-current-display-bottom',passed=True,lowestBottom=lowest,screenHeight=h))
finally:rotate()
shell('am','start','-n',package+'/.MainActivity');time.sleep(.3);setting('拖拽抛射',False)
(out/'physics-panel-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(results,ensure_ascii=True,indent=2))
