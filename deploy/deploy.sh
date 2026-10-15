#!/bin/bash
#==============================================================================
# mfish 四微服务（gateway/oauth/sys/bfces）部署管理脚本
# 在服务器的项目部署根目录（含 docker-compose-deploy.yml 与 .env）执行：
#   chmod +x deploy.sh
#   ./deploy.sh build     # 重新构建镜像并启动
#   ./deploy.sh up        # 启动（镜像已存在时）
#   ./deploy.sh restart   # 重启全部服务
#   ./deploy.sh logs 服务名 # 查看日志，如 ./deploy.sh logs bfces
#   ./deploy.sh ps        # 查看运行状态
#   ./deploy.sh down      # 停止并移除容器
#==============================================================================
COMPOSE_FILE="docker-compose-deploy.yml"
ENV_FILE=".env"

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "未找到 $COMPOSE_FILE，请在部署根目录执行本脚本"
    exit 1
fi
if [ ! -f "$ENV_FILE" ]; then
    echo "未找到 $ENV_FILE，请先执行: cp .env.deploy.example .env 并填写 Nacos 信息"
    exit 1
fi

DC="docker compose --env-file $ENV_FILE -f $COMPOSE_FILE"

case "$1" in
    build)  $DC up -d --build ;;
    up)     $DC up -d ;;
    restart)
            if [ -n "$2" ]; then $DC restart "$2"; else $DC restart; fi
            ;;
    logs)   $DC logs -f --tail=300 "${2:-}" ;;
    ps)     $DC ps ;;
    down)   $DC down ;;
    *)
        echo "用法: $0 {build|up|restart|logs [服务名]|ps|down}"
        echo "服务名: gateway / oauth / sys / bfces"
        exit 1
        ;;
esac
