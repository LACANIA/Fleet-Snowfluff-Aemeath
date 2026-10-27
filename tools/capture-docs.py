"""Capture real v0.2 screenshots in the isolated .test app; retain release app data."""
from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess,time,json,struct,argparse

source=Path(__file__).with_name('multi-test.py')
exec(compile(source.read_text(encoding='utf-8').split('\nassert package.endswith')[0],str(source),'exec'))

def write_preferences(name,values):
    root=ET.Element('map')
    for key,value in values.items():
        if isinstance(value,bool):ET.SubElement(root,'boolean',name=key,value=str(value).lower())
        elif isinstance(value,int):ET.SubElement(root,'int',name=key,value=str(value))
        elif isinstance(value,float):ET.SubElement(root,'float',name=key,value=str(value))
        else:ET.SubElement(root,'string',name=key).text=value
    assert package.endswith('.test') and name.replace('_','').replace('-','').isalnum()
    subprocess.run([adb,'-s',serial,'exec-in','run-as',package,'sh','-c',
                    'cat > shared_prefs/'+name+'.xml'],input=ET.tostring(root,encoding='utf-8'),check=True,timeout=20)

def capture(name):
    time.sleep(.4)
    (project/'docs'/name).write_bytes(call('exec-out','screencap','-p',binary=True))
    print('Captured '+name,flush=True)

assert package.endswith('.test') and package!=production
parser=argparse.ArgumentParser();parser.add_argument('--settings-only',action='store_true');args=parser.parse_args()
manager=os.environ.get('MUMU_MANAGER');rotated=False
def dimensions():return struct.unpack('>II',call('exec-out','screencap','-p',binary=True)[16:24])
if dimensions()[0]>dimensions()[1] and manager:
    subprocess.run([manager,'control','-v',os.environ.get('MUMU_INSTANCE','0'),'tool','func','--name','rotate'],check=True,stdout=subprocess.PIPE,timeout=30)
    rotated=True;time.sleep(1)
shell('am','force-stop',production)
call('install','-r',project/('dist/Flying-Edelweiss-'+version+'-ui-test.apk'))
shell('cmd','appops','set',package,'SYSTEM_ALERT_WINDOW','allow')
try:
    start();shell('am','force-stop',package)
    roles=[('aemeath','爱弥斯'),('phoebe','菲比'),('shorekeeper','守岸人'),('phrolova','弗洛洛')]
    pets=['sample-'+role for role,_ in roles]
    write_preferences('flights',dict(schema=1,instances=json.dumps(pets),selected=pets[0],serial=4,
                                    enabled=not args.settings_only,allowOverlap=True,layoutRevision=0))
    for index,((role,name),id) in enumerate(zip(roles,pets)):
        write_preferences('pet_instance_'+id,dict(character=role,name=name+' '+str(index+1),visible=True,
                                                 roam=False,throw=False,chatter=False,opacity=1.0,size=156,x=.5,y=.5))
    start()
    if not args.settings_only:
        time.sleep(.5);home()
        screen_width,screen_height=dimensions()
        for index,id in enumerate(pets):
            l,t,r,b=windows()[id][0]
            x=int(screen_width*(.29 if index%2==0 else .71))
            y=int(screen_height*(.40 if index<2 else .65))
            shell('input','swipe',(l+r)//2,(t+b)//2,x,y,1200)
        capture('desktop-pets.png')
        start();button('全部收起');start()
    capture('settings.png')
finally:
    shell('am','force-stop',package)
    call('uninstall',package,check=False)
    if rotated:
        subprocess.run([manager,'control','-v',os.environ.get('MUMU_INSTANCE','0'),'tool','func','--name','rotate'],check=True,stdout=subprocess.PIPE,timeout=30)
    shell('am','start','-n',production+'/.MainActivity')
