import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw

base = Path(__file__).resolve().parents[2]
task = Path(__file__).resolve().parents[1]
sheet = Image.open(base / 'aemeath-hachimi-reference/assets/spritesheet.webp').convert('RGBA')
out = Image.new('RGB', (8 * 192, 6 * 240), '#f3edf2')
draw = ImageDraw.Draw(out)
for row in range(9, 15):
    for col in range(8):
        frame = sheet.crop((col * 192, row * 208, (col + 1) * 192, (row + 1) * 208))
        out.paste(frame, (col * 192, (row - 9) * 240), frame)
    draw.text((8, (row - 9) * 240 + 211), 'hachimi row ' + str(row), fill='black')
out.save(task / 'extra-actions-inspection.png')
records = []
for path in sorted((base / 'aemeath-culture-reference/assets/sources').glob('*.gif')):
    gif = Image.open(path)
    records.append({'file':path.name,'size':gif.size,'frames':gif.n_frames,'duration':gif.info.get('duration'),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
(task / 'asset-inspection.json').write_text(json.dumps(records, indent=2) + '\n')
print(json.dumps(records, indent=2))
