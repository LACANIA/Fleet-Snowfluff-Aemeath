import json
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image
project=Path(__file__).resolve().parents[1]
assets=project/'app/src/main/assets'
config=json.loads((assets/'animations.json').read_text(encoding='utf-8'))
sheet=Image.open(assets/'pet-atlas.png').convert('RGBA')
ids=[]
for action in config['actions']:
    assert action['id'] not in ids
    ids.append(action['id'])
    assert len(action['frames'])==len(action['durations'])>0
    for index,delay in zip(action['frames'],action['durations']):
        assert 0<=index<config['frameCount'] and delay>=40
        x=(index%config['columns'])*config['cellWidth'];y=(index//config['columns'])*config['cellHeight']
        frame=sheet.crop((x,y,x+config['cellWidth'],y+config['cellHeight']))
        assert frame.getchannel('A').getbbox() is not None,(action['id'],index,'blank frame')
assert {'idle','walk-left','walk-right','wave','nap'}.issubset(ids)
assert sheet.getpixel((0,0))[3]==0,'Atlas must retain a transparent background'
manifest=ET.parse(project/'app/src/main/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
permissions=[item.attrib[ns+'name'] for item in manifest.findall('uses-permission')]
assert 'android.permission.INTERNET' not in permissions
service=manifest.find('application/service')
assert service.attrib[ns+'exported']=='false'
assert service.attrib[ns+'stopWithTask']=='false'
assert service.attrib[ns+'foregroundServiceType']=='specialUse'
report={'actions':len(ids),'referencedFrames':sum(len(a['frames']) for a in config['actions']),'uniqueFrames':config['frameCount'],'nonemptyFrames':True,'transparentAtlas':True,'networkPermission':False,'serviceStopsWithActivity':False,'serviceExported':False,'permissions':permissions}
(project/'test-results').mkdir(exist_ok=True)
(project/'test-results/asset-and-manifest-results.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
