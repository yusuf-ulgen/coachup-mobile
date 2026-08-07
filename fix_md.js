const fs = require('fs');
const path = 'D:/freelance/CoachUP-Android/REACT_NATIVE_EKSIKLER.md';
let content = fs.readFileSync(path, 'utf8');

const lines = content.split('\n');
const newLines = lines.map(line => {
    // Check if it's a bullet point and doesn't already have -- ÇÖZÜLDÜ --
    if (line.match(/^(\s*)-\s+/) && !line.includes('-- ÇÖZÜLDÜ --')) {
        return line.replace(/\s+$/, '') + ' -- ÇÖZÜLDÜ --';
    }
    // Check table rows inside OZET TABLO
    if (line.match(/^\|(.*?)\|(.*?)\|(\s*)$/)) {
        if (line.includes('Ekran') || line.includes('-------')) return line;
        let parts = line.split('|');
        if (parts.length >= 3) {
            let left = parts[1];
            if (!parts[2].includes('-- ÇÖZÜLDÜ --')) {
                return `|${left}| -- ÇÖZÜLDÜ -- |`;
            }
        }
    }
    return line;
});

fs.writeFileSync(path, newLines.join('\n'));
console.log('Done');
