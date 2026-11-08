#!/data/data/com.termux/files/usr/bin/bash
# Uso: bash atualizar.sh  (dentro de ~/LauncherAranha)
set -e
git add -A
git -c user.name="lighttwave444" -c user.email="lighttwave444@users.noreply.github.com" commit -qm "Atualizacao" || true
git push
sleep 15
gh run watch
