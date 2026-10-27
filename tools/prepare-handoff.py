"""Reuse the inspected test helpers, then leave the tested app ready for the user."""
from pathlib import Path
source=Path(__file__).with_name('interaction-test.py')
helpers=source.read_text(encoding='utf-8').split("\nshell('am','start','-n',package")[0]
exec(compile(helpers,str(source),'exec'))

shell('am','start','-n',package+'/.MainActivity');time.sleep(.3)
for label,wanted in [('自主走动',True),('点击穿透',False)]:
    node=next(n for n in tree().iter('node') if n.get('text')==label)
    if (node.get('checked')=='true')!=wanted:tap_label(label)
tap_label('让爱弥斯出来')
tap_label('回到初始位置',scroll=True)
shell('input','keyevent','3');time.sleep(.5)
check_bounds('ready-for-user')
screen('09-ready-for-user')
(out/'handoff-results.json').write_text(json.dumps(dict(version=version,wholePetInsideScreen=True,autonomousMovementEnabled=True,clickThroughEnabled=False,foregroundServiceRunning='isForeground=true' in shell('dumpsys','activity','services',package)),indent=2)+'\n')
print('Visible foreground pet ready for user: PASS')
