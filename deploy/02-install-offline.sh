#!/bin/bash
#==============================================================================
# 【在离线 RHEL 9.8 部署服务器上执行】
# 前置：以下内容已放到 /plm/windflow/app/ （本脚本所在目录的上一级）
#   docker-compose-deploy.yml、.env、deploy/、rpms/、images/mfish-images.tar.gz
#
# 作用：
#   1. 离线安装 Docker（卸载冲突的 podman/buildah）
#   2. 导入四个微服务镜像
#   3. 放行防火墙端口 8888/9218/9221/9241
#   4. 按 oauth -> sys -> bfces -> gateway 顺序启动
#
# 执行：
#   cd /plm/windflow/app
#   sed -i 's/\r$//' deploy/*.sh && chmod +x deploy/*.sh
#   ./deploy/02-install-offline.sh
#==============================================================================
set -e

cd "$(dirname "$0")/.."
ROOT=$(pwd)
echo "部署根目录: $ROOT"

[ "$(id -u)" -eq 0 ] || { echo "请使用 root 执行"; exit 1; }
[ -f docker-compose-deploy.yml ] || { echo "缺少 docker-compose-deploy.yml"; exit 1; }
[ -f .env ] || { echo "缺少 .env，请先 cp .env.deploy.example .env 并填写 Nacos 信息"; exit 1; }
[ -d rpms ] || { echo "缺少 rpms/ 目录（Docker 离线安装包）"; exit 1; }

echo "==> [1/5] 卸载与 docker-ce 冲突的 podman/buildah..."
dnf remove -y podman podman-plugins buildah containerd runc 2>/dev/null || true

echo "==> [2/5] 离线安装 Docker..."
if ! command -v docker >/dev/null 2>&1; then
    dnf install -y ./rpms/*.rpm
fi
mkdir -p /etc/docker
# 纯内网无需 registry-mirrors；仅配置日志轮转，防止 json 日志撑爆磁盘
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
EOF
systemctl daemon-reload
systemctl enable --now docker
docker compose version

echo "==> [3/5] 导入微服务镜像..."
if [ -f images/mfish-images.tar.gz ]; then
    gunzip -c images/mfish-images.tar.gz | docker load
elif [ -f images/mfish-images.tar ]; then
    docker load -i images/mfish-images.tar
else
    echo "未找到 images/mfish-images.tar(.gz)"; exit 1
fi
docker images | grep mfish || true

echo "==> [4/5] 创建日志目录并放行防火墙端口..."
mkdir -p logs/gateway logs/oauth logs/sys logs/bfces volumes/sys/code
if systemctl is-active --quiet firewalld; then
    for PORT in 8888 9218 9221 9241; do
        firewall-cmd --permanent --add-port=${PORT}/tcp
    done
    firewall-cmd --reload
    echo "    防火墙已放行: 8888(网关) 9218(oauth) 9221(sys) 9241(bfces)"
else
    echo "    firewalld 未运行，跳过端口放行"
fi

echo "==> [5/5] 启动四个微服务（oauth -> sys -> bfces -> gateway）..."
DC="docker compose --env-file .env -f docker-compose-deploy.yml"
$DC up -d oauth
$DC up -d sys
$DC up -d bfces
$DC up -d gateway

sleep 5
$DC ps

cat <<'TIP'

==================== 启动完成 ====================
常用命令（在 /plm/windflow/app 下）：
  docker compose --env-file .env -f docker-compose-deploy.yml logs -f --tail=200 bfces
  docker compose --env-file .env -f docker-compose-deploy.yml ps
  docker compose --env-file .env -f docker-compose-deploy.yml restart gateway

验证：
  1. Nacos 控制台「服务管理」应看到 mf-oauth / mf-sys / mf-bfces / mf-gateway 实例
  2. curl http://本机IP:8888/oauth2/accessToken （应返回业务 JSON 而非拒绝连接）
  3. Swagger: http://本机IP:8888/swagger-ui/index.html
==================================================
TIP
