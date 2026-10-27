"""Check a real cross-application tap below the pet, then restore touch."""
from pathlib import Path
source=Path(__file__).with_name('interaction-test.py')
exec(compile(source.read_text(encoding='utf-8').split("\nshell('am','start','-n',package")[0],str(source),'exec'))
out=project/'test-results'/('v'+version);out.mkdir(parents=True,exist_ok=True)

shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
control=next(n for n in tree().iter('node') if n.get('text')=='点击穿透')
if control.get('checked')=='true':tap_label('点击穿透')
shell('am','start','-a','android.settings.SETTINGS');time.sleep(.4)
node=next(n for n in tree().iter('node') if n.get('text')=='显示')
l,t,r,b=map(int,re.findall(r'\d+',node.get('bounds')));target=((l+r)//2,(t+b)//2)
shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
tap_label('让爱弥斯出来');time.sleep(.3)
left,top,right,bottom=bounds()[0]
shell('input','swipe',str((left+right)//2),str((top+bottom)//2),str(target[0]),str(target[1]),'350')
tap_label('点击穿透');time.sleep(.2)
rectangle,block=bounds();assert 'NOT_TOUCHABLE' in block and 'alpha=0.75' in block
assert rectangle[0]<=target[0]<=rectangle[2] and rectangle[1]<=target[1]<=rectangle[3]
shell('am','start','-a','android.settings.SETTINGS');time.sleep(.4)
screen('03-click-through-before')
shell('input','tap',str(target[0]),str(target[1]));time.sleep(.4)
texts=[n.get('text') for n in tree().iter('node') if n.get('text')]
assert '字体大小' in texts and '网络和互联网' not in texts,texts
screen('04-underlying-display-page-opened')
results.append(dict(check='cross-app-tap-under-pet-opens-display-settings',passed=True,petBounds=rectangle,tap=target,alpha=.75))

shell('cmd','statusbar','expand-notifications');time.sleep(.5)
root=tree()
if not any(n.get('text')=='恢复触控' for n in root.iter('node')):
    expand=next((n for n in root.iter('node') if n.get('content-desc') in ['展开','Expand']),None)
    if expand is not None:
        l,t,r,b=map(int,re.findall(r'\d+',expand.get('bounds')))
        shell('input','tap',str((l+r)//2),str((t+b)//2));time.sleep(.3)
if any(n.get('text')=='恢复触控' for n in tree().iter('node')):
    tap_label('恢复触控');time.sleep(.3)
    results.append(dict(check='notification-restores-pet-touch',passed=True))
else:
    results.append(dict(check='notification-restores-pet-touch',passed=None,reason='MuMu does not expand its notification shade; check the notification action on a physical phone.'))
shell('cmd','statusbar','collapse');time.sleep(.3)
shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
control=next(n for n in tree().iter('node') if n.get('text')=='点击穿透')
if control.get('checked')=='true':tap_label('点击穿透')
assert 'NOT_TOUCHABLE' not in bounds()[1]
results.append(dict(check='app-switch-restores-pet-touch',passed=True))

shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
tap_label('回到初始位置',scroll=True)
shell('input','keyevent','3');time.sleep(.4)
check_bounds('ready-for-user');screen('05-ready-for-user')
(out/'click-through-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(results,ensure_ascii=False,indent=2))
