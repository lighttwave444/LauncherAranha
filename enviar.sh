#!/data/data/com.termux/files/usr/bin/bash
# Uso: bash enviar.sh   (dentro da pasta LauncherAranha)
set -e
pkg install -y git gh >/dev/null
gh auth status >/dev/null 2>&1 || gh auth login
git init -q -b main 2>/dev/null || true
git add -A
git -c user.name="lighttwave444" -c user.email="lighttwave444@users.noreply.github.com" commit -qm "Launcher Aranha" || true
if git remote get-url origin >/dev/null 2>&1; then
  git push -u origin main
else
  gh repo create lighttwave444/LauncherAranha --public --source=. --remote=origin --push
fi
echo "Enviado! Acompanhe em: https://github.com/lighttwave444/LauncherAranha/actions"
echo "O APK aparece em Releases quando terminar (uns 5 a 8 min)."
