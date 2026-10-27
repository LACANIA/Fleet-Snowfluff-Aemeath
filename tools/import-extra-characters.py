"""Normalize user-referenced frame packs. Download inputs separately; see conversion reports."""
from pathlib import Path
from PIL import Image
import hashlib,json,math,subprocess,sys

project=Path(__file__).resolve().parents[1];root=project/'app/src/main/assets';inputs=project/'tools/downloads'
subprocess.run([sys.executable,str(project/'tools/import-phoebe.py')],check=True)
def write(role,name,source,revision,actions,cell,body):
    width,height=cell;frames=[];indices={};encoded=[];out=root/'characters'/role;out.mkdir(parents=True,exist_ok=True)
    for key,label,images,durations in actions:
        sequence=[]
        for image in images:
            image=image.convert('RGBA');image.thumbnail((width,height),Image.Resampling.LANCZOS);frame=Image.new('RGBA',cell);frame.alpha_composite(image,((width-image.width)//2,height-image.height))
            digest=hashlib.sha256(frame.tobytes()).hexdigest()
            if digest not in indices:indices[digest]=len(frames);frames.append(frame)
            sequence.append(indices[digest])
        encoded.append(dict(id=key,label=label,frames=sequence,durations=durations))
    columns=math.ceil(math.sqrt(len(frames)*height/width));rows=math.ceil(len(frames)/columns);atlas=Image.new('RGBA',(columns*width,rows*height))
    for i,frame in enumerate(frames):atlas.alpha_composite(frame,((i%columns)*width,(i//columns)*height))
    atlas.save(out/'pet-atlas.png',optimize=True)
    (out/'animations.json').write_text(json.dumps(dict(cellWidth=width,cellHeight=height,columns=columns,frameCount=len(frames),actions=encoded),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (out/'SOURCE-NOTICE.txt').write_text(name+'动画来源：'+source+'\n参考提交：'+revision+'\n角色与原美术权利归原作者和相应权利方所有；本项目代码许可不覆盖第三方美术素材。仅转换动画图片。\n',encoding='utf-8')
    (out/'conversion-report.json').write_text(json.dumps(dict(source=source,revision=revision,uniqueFrames=len(frames),atlasSize=atlas.size,decodedBytes=atlas.width*atlas.height*4,atlasSha256=hashlib.sha256((out/'pet-atlas.png').read_bytes()).hexdigest()),indent=2)+'\n',encoding='utf-8')
    catalog=root/'characters.json';data=json.loads(catalog.read_text(encoding='utf-8'));data['characters']=[item for item in data['characters'] if item['id']!=role]+[dict(id=role,name=name,animations='characters/'+role+'/animations.json',atlas='characters/'+role+'/pet-atlas.png',chatter='',body=body)];catalog.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(role,len(encoded),'actions',len(frames),'unique frames',atlas.size)

shore=[Image.open(inputs/'shorekeeper'/('frame_%02d.png'%i)).convert('RGBA') for i in range(1,25)]
write('shorekeeper','守岸人','https://github.com/ZxlDragonDoctor/desktop-pet--','05e6c32daaff674ac8f904b224626b34a6efddfa',[('idle','安静待机',shore,[250]*24)],(144,160),[.20,.12,.80,.96])
actions=[]
for source,key,label in [('idle','idle','安静待机'),('running-left','walk-left','向左走走'),('running-right','walk-right','向右走走'),('waving','wave','打个招呼'),('jumping','jump','害羞反应'),('failed','ponder','小小委屈'),('waiting','waiting','安静等待'),('running','typing','专心打字'),('review','look','认真张望')]:
    gif=Image.open(inputs/'phrolova'/(source+'.gif'));images=[];durations=[]
    for i in range(gif.n_frames):gif.seek(i);images.append(gif.convert('RGBA'));durations.append(max(40,gif.info.get('duration',180)))
    actions.append((key,label,images,durations))
write('phrolova','弗洛洛','https://github.com/sherlidian01-web/Phrolova-codex-pet','d1c5cbd2257107f877e69abd57b47bdffc3e8605',actions,(192,208),[.20,.12,.80,.96])
