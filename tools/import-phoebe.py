"""Convert the user-referenced Phoebe animation pack, retaining provenance and timings."""
import argparse,hashlib,json,math
from pathlib import Path
from PIL import Image

parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path,default=Path(__file__).resolve().parent/'downloads/phoebe');args=parser.parse_args()
project=Path(__file__).resolve().parents[1];root=project/'app/src/main/assets';out=root/'characters/phoebe';out.mkdir(parents=True,exist_ok=True)
width,height=144,160;frames=[];hashes={};actions=[];provenance=[]
for name,key,label in [('idle','idle','呼吸眨眼'),('push','push','伸伸手'),('eating','eating','吃点东西'),('talk','talk','说说话'),('sleep','nap','打个盹')]:
    source=args.source/(name+'.gif');gif=Image.open(source);raw=[];durations=[];bounds=None
    for i in range(gif.n_frames):
        gif.seek(i);frame=gif.convert('RGBA');box=frame.getchannel('A').getbbox()
        if box:bounds=box if bounds is None else (min(bounds[0],box[0]),min(bounds[1],box[1]),max(bounds[2],box[2]),max(bounds[3],box[3]))
        raw.append(frame);durations.append(max(40,gif.info.get('duration',100)))
    if bounds is None:raise ValueError('Blank source '+name)
    indices=[];times=[];pending=0;selected=None
    def emit(image,delay):
        image=image.crop(bounds);image.thumbnail((width-8,height-8),Image.Resampling.LANCZOS)
        canvas=Image.new('RGBA',(width,height));canvas.alpha_composite(image,((width-image.width)//2,height-image.height-4))
        digest=hashlib.sha256(canvas.tobytes()).hexdigest()
        if digest not in hashes:hashes[digest]=len(frames);frames.append(canvas)
        index=hashes[digest]
        if indices and indices[-1]==index:times[-1]+=delay
        else:indices.append(index);times.append(delay)
    for image,delay in zip(raw,durations):
        if selected is None:selected=image
        pending+=delay
        if pending>=100:emit(selected,pending);pending=0;selected=None
    if selected is not None:emit(selected,pending)
    actions.append(dict(id=key,label=label,frames=indices,durations=times));provenance.append(dict(file=source.name,sha256=hashlib.sha256(source.read_bytes()).hexdigest(),originalFrames=len(raw),storedSequenceFrames=len(indices),totalDurationMs=sum(times)))
    del raw
columns=math.ceil(math.sqrt(len(frames)*height/width));rows=math.ceil(len(frames)/columns)
atlas=Image.new('RGBA',(columns*width,rows*height))
for i,frame in enumerate(frames):atlas.alpha_composite(frame,((i%columns)*width,(i//columns)*height))
atlas.save(out/'pet-atlas.png',optimize=True)
(out/'animations.json').write_text(json.dumps(dict(cellWidth=width,cellHeight=height,columns=columns,frameCount=len(frames),actions=actions),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(out/'SOURCE-NOTICE.txt').write_text('菲比动画来源：https://github.com/llors-chen/Feibi_desktop\n参考提交：99750437de188ddaa3430cc6fffb9c991f906806\n角色与原美术权利归原作者和相应权利方所有；本项目代码许可不覆盖第三方美术素材。\n保留五组 GIF 的总时长，约 10 FPS 采样，透明图集重排；仅使用动画图片。\n',encoding='utf-8')
(out/'conversion-report.json').write_text(json.dumps(dict(source='https://github.com/llors-chen/Feibi_desktop',revision='99750437de188ddaa3430cc6fffb9c991f906806',uniqueFrames=len(frames),atlasSize=atlas.size,decodedBytes=atlas.width*atlas.height*4,actions=provenance),indent=2)+'\n',encoding='utf-8')
local=root/'characters.json';data=json.loads(local.read_text(encoding='utf-8')) if local.exists() else {'characters':[]}
data['characters']=[item for item in data['characters'] if item['id']!='phoebe']+[dict(id='phoebe',name='菲比',animations='characters/phoebe/animations.json',atlas='characters/phoebe/pet-atlas.png',chatter='',body=[.22,.14,.78,.96])]
local.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(character='phoebe',actions=len(actions),uniqueFrames=len(frames),atlas=atlas.size,decodedMiB=round(atlas.width*atlas.height*4/1048576,1)),ensure_ascii=True))
