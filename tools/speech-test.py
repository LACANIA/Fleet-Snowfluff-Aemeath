"""Check local speech preview, lifetime and cross-app touch below the bubble."""
from pathlib import Path
source=Path(__file__).with_name('physics-panel-test.py')
exec(compile(source.read_text(encoding='utf-8').split("\nshell('am','start','-n',package")[0],str(source),'exec'))

def speech():
    for block in re.split(r'\n  Window #',shell('dumpsys','window','windows')):
        if 'AemeathSpeech' not in block or 'appop=SYSTEM_ALERT_WINDOW' not in block:continue
        match=re.search(r'mFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
        if match:return tuple(map(int,match.groups())),block
    return None
def preview():button('看看台词气泡');assert speech(),'Preview must show a bubble'
def separate():
    rectangle,block=speech();l,t,r,b=rectangle;pl,pt,pr,pb=bounds()[0];w,h=screen()
    assert 0<=l<r<=w and 0<=t<b<=h
    assert r<=pl or l>=pr or b<=pt or t>=pb
    assert 'NOT_TOUCHABLE' in block and 'alpha=0.75' in block
    return rectangle

shell('am','force-stop',package);shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
setting('随机小气泡',False);setting('自主走动',False);setting('拖拽抛射',False);setting('点击穿透',False)
button('让爱弥斯出来');preview();first=separate();screen('07-local-speech')
time.sleep(5);assert speech() is None
results.append(dict(check='preview-is-separate-nontouchable-and-expires',passed=True,bounds=first))
print('Speech preview and expiry: PASS',flush=True)

preview();original=screen()
try:
    rotate();assert screen()!=original;assert speech() is None
    results.append(dict(check='rotation-removes-old-speech-window',passed=True))
    print('Speech rotation cleanup: PASS',flush=True)
finally:rotate()

# The target button covers the whole test page, so no pet repositioning is needed.
probe='io.github.lacania.overlayprobe/.ProbeActivity'
shell('am','force-stop','io.github.lacania.overlayprobe');preview();l,t,r,b=speech()[0];target=((l+r)//2,(t+b)//2)
shell('am','start','-n',probe);time.sleep(.25)
l,t,r,b=separate();assert l<=target[0]<=r and t<=target[1]<=b,(target,(l,t,r,b))
assert 'mHasSurface=true' in speech()[1] and 'shown=true' in speech()[1],'Bubble must actually be rendered over the other app'
shell('input','tap',str(target[0]),str(target[1]));time.sleep(.2)
texts=[n.get('text') for n in tree().iter('node')]
assert any((text or '').upper()=='TAP COUNT: 1' for text in texts)
results.append(dict(check='tap-below-speech-opens-another-app',passed=True,tap=target))
print('Visible speech forwards touch to a separate Android UID: PASS',flush=True)

shell('am','start','-n',package+'/.MainActivity');time.sleep(.3);setting('随机小气泡',True);preview()
setting('随机小气泡',False);assert speech() is None
results.append(dict(check='disabling-random-speech-removes-visible-bubble',passed=True))
preview();x,y=center();shell('input','swipe',str(x),str(y),str(x),str(y),'850')
assert speech() is None;assert any(n.get('text')=='爱弥斯的小动作' for n in tree().iter('node'))
tap_label('×');results.append(dict(check='pet-menu-replaces-speech-without-conflict',passed=True))
(out/'speech-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(results,ensure_ascii=True,indent=2))
