"""Merge the two referenced pet projects into a reproducible Android animation pack."""
import hashlib
import json
from pathlib import Path
import shutil
from PIL import Image, ImageOps, ImageDraw

project = Path(__file__).resolve().parents[1]
base = project.parent
assets = project / 'app/src/main/assets'
assets.mkdir(parents=True, exist_ok=True)
sources = base / 'aemeath-culture-reference/assets/sources'
hachimi = Image.open(base / 'aemeath-hachimi-reference/assets/spritesheet.webp').convert('RGBA')
cell_w, cell_h, columns = 200, 208, 8
actions = []
stored_frames = []
frame_index = {}
removed = []

def clean(image):
    canvas = Image.new('RGBA', (cell_w, cell_h))
    canvas.alpha_composite(image, ((cell_w - image.width) // 2, cell_h - image.height))
    return canvas

def add_action(key, label, frames, durations, source, **extra):
    indices = []
    for image in frames:
        image = clean(image)
        digest = hashlib.sha256(image.tobytes()).hexdigest()
        if digest not in frame_index:
            frame_index[digest] = len(stored_frames)
            stored_frames.append(image)
        indices.append(frame_index[digest])
    # The whole visual sequence, including timing, determines duplicate actions.
    signature = (tuple(indices), tuple(durations))
    for existing in actions:
        if (tuple(existing['frames']), tuple(existing['durations'])) == signature:
            removed.append({'id':key,'sameAs':existing['id']})
            return
    actions.append(dict(id=key, label=label, frames=indices, durations=durations, source=source, **extra))

originals = [
    ('idle','呼吸眨眼','d86375e8'),
    ('walk-right','向右走走','455f181e'),
    ('wave','打个招呼','24a4bbf1'),
    ('jump','开心蹦跳','53a58a26'),
    ('ponder','困惑冒泡','216d4f2d'),
    ('groove','墨镜摇摆','1a05d5b6'),
]
for key, label, prefix in originals:
    path = next(sources.glob(prefix + '*.gif'))
    gif = Image.open(path)
    frames, times = [], []
    for frame in range(gif.n_frames):
        gif.seek(frame)
        frames.append(gif.convert('RGBA'))
        times.append(max(40, gif.info.get('duration', 110)))
    add_action(key, label, frames, times, 'culture-flask/dsh-aemeath-pet:' + path.name)
    if key == 'walk-right':
        add_action('walk-left','向左走走',[ImageOps.mirror(fr) for fr in frames],times,'culture-flask/dsh-aemeath-pet:mirrored-walk')

for row, key, label in [
    (9,'happy','开心摆手'),
    (10,'blush','害羞眨眼'),
    (11,'look','好奇张望'),
    (12,'daydream','安静发呆'),
    (13,'nap','偷偷打盹'),
    (14,'peek','开心冒头'),
]:
    frames = [hachimi.crop((col*192,row*208,(col+1)*192,(row+1)*208)) for col in range(8)]
    add_action(key,label,frames,[180]*8,'hachimi-ai/dsh-aemeath:row-' + str(row))

# culture's waiting/review use exactly the same source and sampling; both look rows
# repeat the first idle frame. The overlapping hachimi base rows add no new gestures.
aliases = {
    'culture:failed':'ponder', 'culture:waiting':'ponder','culture:review':'ponder',
    'culture:look-front':'idle','culture:look-back':'idle',
    'hachimi:idle':'idle','hachimi:running-right':'walk-right','hachimi:running-left':'walk-left',
    'hachimi:waving':'wave','hachimi:jumping':'jump',
}
rows = (len(stored_frames) + columns - 1) // columns
atlas = Image.new('RGBA', (columns * cell_w, rows * cell_h))
for i, frame in enumerate(stored_frames):
    atlas.alpha_composite(frame, ((i % columns) * cell_w, (i // columns) * cell_h))
atlas.save(assets / 'pet-atlas.png', optimize=True)
config = dict(cellWidth=cell_w,cellHeight=cell_h,columns=columns,frameCount=len(stored_frames),actions=actions)
(assets / 'animations.json').write_text(json.dumps(config,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
shutil.copyfile(base/'aemeath-culture-reference/LICENSE', assets/'CULTURE-APACHE-2.0.txt')
notice = '''爱弥斯口袋桌宠 · 素材来源

本应用为个人使用的非官方同人安卓适配，角色爱弥斯与《鸣潮》相关视觉设计归相应权利方所有。

融合项目一：https://github.com/hachimi-ai/dsh-aemeath
使用扩展精灵图的日常表情。该项目声明代码 MIT，角色素材仅供个人学习交流。

融合项目二：https://github.com/culture-flask/dsh-aemeath-pet
使用六个原始透明 GIF，保留原始帧与播放时间，左右走动按需镜像。代码许可为 Apache-2.0，协议全文随应用附带。
该项目说明：角色形象归 Kuro Games / 相应权利方所有，原始表情包来自 B 站“Neuro音乐”：
https://www.bilibili.com/opus/1167003396733927448
行与帧编排源自 https://github.com/ChuyuZhong/aemeath-codex-pet 。

拖拽抛射算法移植自 PC2005-cloud/dsh-pet 的 src/shared/physics.ts：
https://github.com/PC2005-cloud/dsh-pet
Copyright (c) 2026 PC2005-cloud，MIT，协议全文随应用附带于 DSH-PET-MIT.txt。
安卓适配加入 dp 缩放、完整窗口边界、触摸中断与独立开关。

本应用素材处理：原始 GIF 抽帧、透明像素清理、图集重排、重复帧存储去重和动画名称本地化。
没有将 DSH 皮肤、壁纸或工作状态连接放入应用。没有使用 AI 重新绘制角色。
不对角色及原始美术素材授予额外商业许可。
'''
(assets/'NOTICES.txt').write_text(notice,encoding='utf-8')
report=dict(actionCount=len(actions),uniqueStoredFrames=len(stored_frames),sequenceFrames=sum(len(a['frames']) for a in actions),removedExactActions=removed,semanticAliases=aliases,atlasSize=atlas.size,atlasSha256=hashlib.sha256((assets/'pet-atlas.png').read_bytes()).hexdigest())
(project/'merge-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
overview=Image.new('RGB',(4*220,((len(actions)+3)//4)*245),'#fff5fa')
draw=ImageDraw.Draw(overview)
for n, action in enumerate(actions):
    frame=stored_frames[action['frames'][0]]
    x=(n%4)*220+10;y=(n//4)*245
    overview.paste(frame,(x,y),frame)
    draw.text((x,y+211),action['id'],fill='#3f2844')
overview.save(project/'merged-actions-preview.png')
print(json.dumps(report,ensure_ascii=False,indent=2))
