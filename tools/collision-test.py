"""Observe real walk deadlines and chained flight in the isolated debug application."""
from pathlib import Path
source=Path(__file__).with_name('capture-docs.py')
exec(compile(source.read_text(encoding='utf-8').split('\nassert package.endswith')[0],str(source),'exec'))
import struct

out=project/'test-results'/'collision-modes';out.mkdir(parents=True,exist_ok=True)
results=[];pet_ids=['collision-a','collision-b','collision-c']

def state():
    dump=shell('dumpsys','activity','service',package+'/'+production+'.PetService')
    match=re.search(r'FLYING_STATE (\[.*\])',dump)
    assert match is not None,dump
    return {item['id']:item for item in json.loads(match[1])}

def drag(id,x,y):
    current=state()[id];cx=current['x']+current['width']//2;cy=current['y']+current['height']//2
    shell('input','swipe',cx,cy,x+current['width']//2,y+current['height']//2,1800)

def play(id,animation):
    result=shell('run-as',package,'am','startservice','--user','0','-n',package+'/'+production+'.PetService',
                 '-a','pet.play','--es','petId',id,'--es','animation',animation,check=False)
    if 'Starting service' not in result:
        start()
        tap(locate(lambda n:n.get('content-desc')=='选择要设置的桌宠'));button('爱弥斯 1')
        button('选择这只的动作');button('向右走走');home()

def prepare(walk_knocks):
    shell('am','force-stop',package)
    write_preferences('flights',dict(schema=1,instances=json.dumps(pet_ids),selected=pet_ids[0],serial=3,
                                    enabled=True,allowOverlap=False,walkKnocks=walk_knocks,layoutRevision=0))
    for i,id in enumerate(pet_ids):
        write_preferences('pet_instance_'+id,dict(character='aemeath',name='爱弥斯 '+str(i+1),visible=True,
                                                 roam=False,throw=True,chatter=False,opacity=.2 if i==1 else 1.0,size=100,x=.5,y=.5))
    start();home();time.sleep(.3)
    states=state();w,h=struct.unpack('>II',call('exec-out','screencap','-p',binary=True)[16:24])
    size=states[pet_ids[0]]['width'];height=states[pet_ids[0]]['height'];x=int(w*.18);y=int(h*.28)
    target_x=x+int(size*.6)+18
    drag(pet_ids[0],x,y)
    drag(pet_ids[1],target_x,y)
    drag(pet_ids[2],target_x+35,y+height+35)
    # A slow drag is not a throw; all three must be resting before the walk begins.
    states=state();assert not any(item['flying'] for item in states.values()),states
    gap=states[pet_ids[1]]['x']-states[pet_ids[0]]['x']-.6*size
    assert 4<=gap<=75 and states[pet_ids[0]]['y']==states[pet_ids[1]]['y'],('Fixture must put the target ahead',states)
    return states

assert package.endswith('.test') and package!=production
shell('am','force-stop',production)
call('install','-r',project/('dist/Fleet-Snowfluff-'+version+'-ui-test.apk'))
shell('cmd','appops','set',package,'SYSTEM_ALERT_WINDOW','allow')
try:
    start();shell('am','force-stop',package)
    for knocks in [False,True]:
        initial=prepare(knocks);play(pet_ids[0],'walk-right')
        first=state();deadline=first[pet_ids[0]]['walkUntil'];started=first[pet_ids[0]]['actionStarted']
        assert deadline>first[pet_ids[0]]['now'],first
        samples=[];until=time.monotonic()+4.0
        while time.monotonic()<until:
            samples.append(state());time.sleep(.045)
        (out/('walking-knocks-'+str(knocks).lower()+'.json')).write_text(json.dumps(dict(initial=initial,first=first,samples=samples),indent=2)+'\n',encoding='utf-8')
        walker=[sample[pet_ids[0]] for sample in samples]
        during=[sample for sample in walker if sample['now']<deadline-80]
        assert during and all(sample['walkUntil']==deadline and not sample['flying'] for sample in during),during
        assert walker[-1]['walkUntil']==0 and walker[-1]['action'] not in ('walk-right','walk-left'),walker[-1]
        if knocks:
            assert all(sample['action']=='walk-right' and sample['actionStarted']==started for sample in during),during
            assert max(sample['x'] for sample in walker)>initial[pet_ids[0]]['x']+100
            assert any(sample[pet_ids[1]]['flying'] for sample in samples),'First target never entered flight'
            assert any(sample[pet_ids[2]]['flying'] for sample in samples),'Falling target did not launch the next pet'
            check='walking-source-keeps-original-deadline-and-animation-while-targets-chain-flight'
        else:
            for id in pet_ids[1:]:
                assert all(not sample[id]['flying'] and sample[id]['x']==initial[id]['x'] and sample[id]['y']==initial[id]['y'] for sample in samples),samples
            assert any(sample['direction']==-1 for sample in during),('Blocked walker did not turn away',initial,first,walker[-1])
            check='ordinary-walking-blocks-without-launching-or-moving-idle-targets-and-finishes-on-time'
        results.append(dict(check=check,passed=True));print('PASS '+check,flush=True)
    # The new option is global, default-off, and unavailable while overlap is allowed.
    start();setting('允许桌宠重叠',True)
    option=locate(lambda n:n.get('text')=='走动也能撞飞');assert option.get('enabled')=='false'
    setting('允许桌宠重叠',False)
    option=locate(lambda n:n.get('text')=='走动也能撞飞');assert option.get('enabled')=='true'
    results.append(dict(check='walking-knock-option-follows-overlap-toggle',passed=True))
    print('PASS walking-knock-option-follows-overlap-toggle',flush=True)
    home();play(pet_ids[1],'walk-right');before=state();assert before[pet_ids[1]]['walkUntil']>before[pet_ids[1]]['now']
    shell('run-as',package,'am','startservice','--user','0','-n',package+'/'+production+'.PetService',
          '-a','pet.find','--es','petId',pet_ids[1])
    after=state();target=after[pet_ids[1]]
    assert 400<target['locateUntil']-target['now']<=1000,target
    assert target['action']=='wave' and target['walkUntil']==0 and not target['flying'],(before,after)
    assert target['alpha']>.9 and abs(pet_prefs(pet_ids[1])['opacity']-.2)<.001,target
    assert all(item['locateUntil']==0 for id,item in after.items() if id!=pet_ids[1]),after
    (out/'find-highlight.png').write_bytes(call('exec-out','screencap','-p',binary=True))
    time.sleep(1.15);finished=state()[pet_ids[1]]
    assert finished['locateUntil']<finished['now'] and finished['manualUntil']==0 and finished['action']=='idle' and abs(finished['alpha']-.2)<.001,finished
    results.append(dict(check='find-interrupts-walk-waves-emphasizes-only-target-and-restores-in-one-second',passed=True))
    print('PASS find-interrupts-walk-waves-emphasizes-only-target-and-restores-in-one-second',flush=True)
    start()
    shell('run-as',package,'am','startservice','--user','0','-n',package+'/'+production+'.PetService',
          '-a','pet.find','--es','petId',pet_ids[1])
    focused=state();product=1
    for item in focused.values():
        if item['passing']:product*=1-item['alpha']
        product*=1-item['bubbleAlpha']
    assert focused[pet_ids[1]]['alpha']>=.59 and 1-product<=.75001,focused
    time.sleep(1.15);assert abs(state()[pet_ids[1]]['alpha']-.2)<.001
    results.append(dict(check='find-emphasis-keeps-combined-touch-through-opacity-without-changing-preferences',passed=True))
    print('PASS find-emphasis-keeps-combined-touch-through-opacity-without-changing-preferences',flush=True)
finally:
    (out/'results.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
    shell('am','force-stop',package)
    call('uninstall',package,check=False)
    shell('am','start','-n',production+'/.MainActivity')
