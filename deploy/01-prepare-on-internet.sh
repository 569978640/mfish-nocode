#!/bin/bash
#==============================================================================
# 【在可联网的 RHEL 9.8 机器上执行】
# 作用：
#   1. 在线安装/校验本机 Docker（用于构建镜像）
#   2. 下载 docker-ce el9 离线 RPM 包到 ./rpms（供离线服务器安装）
#   3. 基于本部署包构建 gateway/oauth/sys/bfces 四个镜像
#   4. docker save 导出为 ./images/mfish-images.tar
#
# 前提：部署包已解压，当前目录含 docker-compose-deploy.yml、mf-start/ 四个模块
# 执行：
#   chmod +x deploy/*.sh
#   ./deploy/01-prepare-on-internet.sh
#==============================================================================
set -e

cd "$(dirname "$0")/.."
ROOT=$(pwd)
echo "部署包根目录: $ROOT"

[ "$(id -u)" -eq 0 ] || { echo "请使用 root 执行"; exit 1; }
[ -f docker-compose-deploy.yml ] || { echo "未找到 docker-compose-deploy.yml"; exit 1; }

# .env 缺失时由模板生成
if [ ! -f .env ]; then
    cp .env.deploy.example .env
    echo "已生成 .env，请填写 Nacos 地址后重新执行本脚本（构建不依赖它，但导出清单需要）"
fi

echo "==> [1/4] 安装/校验本机 Docker（在线）..."
if ! command -v docker >/dev/null 2>&1; then
    cat > /etc/yum.repos.d/docker-ce.repo <<'EOF'
[docker-ce-stable]
name=Docker CE Stable - $baseurl
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/9/$basearch/stable
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg
EOF
    dnf install -y dnf-plugins-core
    dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
fi
systemctl enable --now docker
docker compose version

echo "==> [2/4] 下载 el9 离线 RPM 到 ./rpms ..."
mkdir -p rpms
# --downloadonly 只下载不安装，docker-ce 及全部依赖（含 container-selinux）都会进目录
dnf install --downloadonly --downloaddir=rpms -y \
    docker-ce docker-ce-cli containerd.io docker-compose-plugin || true
RP_COUNT=$(find rpms -name '*.rpm' | wc -l)
echo "    共下载 $RP_COUNT 个 RPM"
if ! find rpms -name 'container-selinux*.rpm' | grep -q .; then
    echo "    [警告] rpms 中缺少 container-selinux，请确认本机 AppStream/BaseOS 源可用后重试"
fi

echo "==> [3/4] 构建四个微服务镜像..."
docker compose --env-file .env -f docker-compose-deploy.yml build

echo "==> [4/4] 导出镜像到 ./images/mfish-images.tar ..."
mkdir -p images
IMAGES=$(docker compose --env-file .env -f docker-compose-deploy.yml config --images)
echo "    待导出镜像:"; echo "$IMAGES" | sed 's/^/      - /'
docker save -o images/mfish-images.tar $IMAGES
gzip -f images/mfish-images.tar || true
ls -lh images/

echo ""
echo "==================== 准备完成 ===================="
echo "请将以下内容拷贝到【离线服务器】 /plm/windflow/app/ ："
echo "  1. docker-compose-deploy.yml"
echo "  2. .env"
echo "  3. deploy/ 目录（脚本）"
echo "  4. rpms/ 目录（Docker 离线安装包）"
echo "  5. images/mfish-images.tar.gz（镜像包）"
echo "=================================================="
