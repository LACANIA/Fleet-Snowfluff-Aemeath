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
chatter=json.loads((assets/'chatter.json').read_text(encoding='utf-8'))
line_ids=set()
for line in chatter['lines']:
    assert line['id'] not in line_ids
    line_ids.add(line['id'])
    assert 0<len(line['text'])<=32
    assert line['kind'] in ['game-voice','fan-written']
    assert line['context'] in ['idle','look','wave','nap','groove','throw']
manifest=ET.parse(project/'app/src/main/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
permissions=[item.attrib[ns+'name'] for item in manifest.findall('uses-permission')]
assert 'android.permission.INTERNET' not in permissions
service=manifest.find('application/service')
assert service.attrib[ns+'exported']=='false'
assert service.attrib[ns+'stopWithTask']=='false'
assert service.attrib[ns+'foregroundServiceType']=='specialUse'
report={'actions':len(ids),'referencedFrames':sum(len(a['frames']) for a in config['actions']),'uniqueFrames':config['frameCount'],'nonemptyFrames':True,'transparentAtlas':True,'networkPermission':False,'serviceStopsWithActivity':False,'serviceExported':False,'permissions':permissions}
catalog=json.loads((assets/'characters.json').read_text(encoding='utf-8'))['characters']
characters=[];character_ids=set()
for character in catalog:
    assert character['id'] not in character_ids
    character_ids.add(character['id'])
    animation=json.loads((assets/character['animations']).read_text(encoding='utf-8'))
    atlas=Image.open(assets/character['atlas']).convert('RGBA')
    width,height=animation['cellWidth'],animation['cellHeight'];columns=animation['columns']
    count=animation['frameCount'];action_ids=set()
    assert width>0 and height>0 and columns>0 and count>0
    assert atlas.width==width*columns and atlas.height>=((count+columns-1)//columns)*height
    nonempty=set()
    for action in animation['actions']:
        assert action['id'] not in action_ids
        action_ids.add(action['id'])
        assert len(action['frames'])==len(action['durations'])>0
        for index,delay in zip(action['frames'],action['durations']):
            assert 0<=index<count and delay>=40,(character['id'],action['id'],index,delay)
            if index not in nonempty:
                x=(index%columns)*width;y=(index//columns)*height
                assert atlas.crop((x,y,x+width,y+height)).getchannel('A').getbbox(),(character['id'],index)
                nonempty.add(index)
    assert 'idle' in action_ids and len(nonempty)==count
    assert atlas.getpixel((0,0))[3]==0
    assert len(character['body'])==4
    x0,y0,x1,y1=character['body'];assert 0<=x0<x1<=1 and 0<=y0<y1<=1
    if character['chatter']:assert (assets/character['chatter']).is_file()
    characters.append(dict(id=character['id'],actions=len(action_ids),uniqueFrames=count,decodedBytes=atlas.width*atlas.height*4))
report['characters']=characters
(project/'test-results').mkdir(exist_ok=True)
(project/'test-results/asset-and-manifest-results.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
