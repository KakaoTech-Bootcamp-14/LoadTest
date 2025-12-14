.PHONY: deploy build-local deploy-all deploy-prod deploy-dev help install-node-remote verify-ssh-key

# PEM 키 파일 경로
PEM_KEY ?= .ssh/ktb-14.pem
SSH_USER ?= ubuntu

# 배포 대상 서버 설정
# 형식: 스페이스로 구분된 IP 주소 목록
# 사용법: make deploy-prod DEPLOY_SERVERS="ip1 ip2 ip3"
DEPLOY_SERVERS ?= 3.36.118.176 3.38.209.209
DEV_SERVER ?=

# 실제 배포할 서버 목록 (SERVERS 변수가 제공되면 우선 사용)
SERVERS ?= $(DEPLOY_SERVERS)

# 배포 경로
DEPLOY_PATH ?= /home/ubuntu/ktb-chat-frontend

# SSH 및 RSYNC 옵션
SSH_OPTS := -i $(PEM_KEY) -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null
RSYNC_OPTS := -avz --delete -e "ssh $(SSH_OPTS)"

# 도움말
help:
	@echo "📖 사용 가능한 명령어:"
	@echo ""
	@echo "  make build-local          - 로컬에서 프로덕션 빌드"
	@echo "  make deploy              - 기본 서버에 병렬 배포 (DEPLOY_SERVERS)"
	@echo "  make deploy-prod         - 프로덕션 서버에 병렬 배포"
	@echo "  make deploy-dev          - 개발 서버에 병렬 배포"
	@echo "  make deploy-all          - 모든 서버에 병렬 배포 (DEPLOY_SERVERS + DEV_SERVER)"
	@echo "  make install-node-remote - 원격 서버에 Node.js/npm 설치"
	@echo ""
	@echo "🔧 환경 변수:"
	@echo "  PEM_KEY=.ssh/ktb-14.pem             - SSH PEM 키 파일 경로"
	@echo "  SSH_USER=ubuntu                     - SSH 사용자명"
	@echo "  DEPLOY_SERVERS=3.36.118.176         - 배포할 서버 IP (스페이스로 구분)"
	@echo "  DEV_SERVER=                         - 개발 서버 IP (스페이스로 구분)"
	@echo "  DEPLOY_PATH=/home/ubuntu/...        - 배포 경로"
	@echo ""
	@echo "📝 예시:"
	@echo "  make deploy-prod                                    - 기본 서버에 배포 (Node.js 자동 설치)"
	@echo "  make deploy-prod DEPLOY_SERVERS=\"ip1 ip2 ip3\"       - 여러 서버에 병렬 배포"
	@echo "  make deploy-dev DEV_SERVER=\"ip1 ip2\"                - 개발 서버 여러 개에 배포"
	@echo "  make deploy-all DEPLOY_SERVERS=\"ip1 ip2\" DEV_SERVER=\"ip3 ip4\"  - 모든 서버에 배포"
	@echo "  make install-node-remote DEPLOY_SERVERS=\"ip1 ip2\"   - 수동 Node.js 설치"
	@echo "  make deploy PEM_KEY=.ssh/my-key.pem                 - 커스텀 SSH 키 사용"

# PEM 키 파일 권한 체크 및 설정
check-pem:
	@if [ ! -f "$(PEM_KEY)" ]; then \
		echo "❌ PEM 키 파일을 찾을 수 없습니다: $(PEM_KEY)"; \
		echo "💡 ssh 디렉토리에 PEM 키를 넣어주세요."; \
		exit 1; \
	fi
	@chmod 400 $(PEM_KEY) 2>/dev/null || true
	@echo "✅ PEM 키 확인 완료: $(PEM_KEY)"

# SSH 키 존재 확인 (백엔드와 호환)
verify-ssh-key: check-pem

# 원격 서버에 Node.js 및 npm 설치
install-node-remote: verify-ssh-key
	@echo "📦 Installing Node.js and npm on remote servers..."
	@echo "   Using SSH key: $(PEM_KEY)"
	@echo "   Target servers: $(SERVERS)"
	@echo ""
	@for server in $(SERVERS); do \
		echo "  → Installing Node.js on $$server..."; \
		ssh $(SSH_OPTS) $(SSH_USER)@$$server "\
			echo '  [$$server] Checking Node.js installation...' && \
			if command -v node &> /dev/null; then \
				echo '  [$$server] Node.js already installed: '\$$(node -v); \
			else \
				echo '  [$$server] Node.js not found, installing...' && \
				curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash - && \
				sudo apt-get install -y nodejs && \
				echo '  [$$server] Node.js installed: '\$$(node -v) && \
				echo '  [$$server] npm installed: '\$$(npm -v); \
			fi" && \
		echo "  ✅ $$server Node.js setup completed" || \
		echo "  ❌ $$server Node.js setup failed"; \
	done
	@echo ""
	@echo "✅ All Node.js installations completed!"

# 로컬에서 프로덕션 빌드
build-local:
	@echo "🏗️  Building locally..."
	npm run build:production
	@echo "✅ Local build completed!"

# 단일 서버 배포 함수 (병렬 실행용)
# 인자: $$server (서버 IP 주소)
define deploy_single_server
	echo ""; \
	echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"; \
	echo "→ 배포 대상: $$server"; \
	echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"; \
	echo "  📡 서버 연결 테스트..."; \
	ssh $(SSH_OPTS) $(SSH_USER)@$$server "echo '✅ 연결 성공'" || (echo "❌ 서버 연결 실패: $$server" && exit 1); \
	echo "  🔍 Node.js 확인 및 설치..."; \
	ssh $(SSH_OPTS) $(SSH_USER)@$$server "\
		if command -v node &> /dev/null; then \
			echo '    [$$server] Node.js 이미 설치됨: '\$$(node -v) '(npm: '\$$(npm -v)')'; \
		else \
			echo '    [$$server] Node.js 설치 중...' && \
			curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash - && \
			sudo apt-get install -y nodejs && \
			echo '    [$$server] Node.js 설치 완료: '\$$(node -v) '(npm: '\$$(npm -v)')'; \
		fi"; \
	echo "  📁 배포 디렉토리 생성..."; \
	ssh $(SSH_OPTS) $(SSH_USER)@$$server "mkdir -p $(DEPLOY_PATH)"; \
	echo "  📦 Standalone 빌드 복사..."; \
	rsync $(RSYNC_OPTS) --exclude='*.log' --exclude='.env*' --exclude="server.pid" --exclude='/package.json' .next/standalone/ $(SSH_USER)@$$server:$(DEPLOY_PATH)/; \
	echo "  🎨 Static 파일 복사..."; \
	rsync $(RSYNC_OPTS) .next/static $(SSH_USER)@$$server:$(DEPLOY_PATH)/.next/; \
	echo "  🖼️  Public 파일 복사..."; \
	rsync $(RSYNC_OPTS) public $(SSH_USER)@$$server:$(DEPLOY_PATH)/; \
	echo "  🔧 재시작 스크립트 복사..."; \
	rsync $(RSYNC_OPTS) restart.sh $(SSH_USER)@$$server:$(DEPLOY_PATH)/; \
	echo "  🔄 서버 재시작..."; \
	ssh $(SSH_OPTS) $(SSH_USER)@$$server "cd $(DEPLOY_PATH) && chmod +x restart.sh && ./restart.sh"; \
	echo "✅ $$server 배포 완료!"; \
	echo ""
endef

# 기본 배포 (여러 서버 병렬 배포)
deploy: check-pem build-local
	@echo "📦 배포 시작 (병렬)..."
	@if [ -z "$(SERVERS)" ]; then \
		echo "❌ SERVERS가 설정되지 않았습니다."; \
		exit 1; \
	fi
	@echo "   Target servers: $(SERVERS)"
	@echo ""
	@pids=""; \
	for server in $(SERVERS); do \
		echo "  → Starting deployment to $$server..."; \
		($(deploy_single_server) && \
		 echo "  ✅ $$server deployment completed" || \
		 echo "  ❌ $$server deployment failed") & \
		pids="$$pids $$!"; \
	done; \
	echo ""; \
	echo "⏳ Waiting for all deployments to complete..."; \
	wait $$pids; \
	echo ""
	@echo "🎉 배포 완료!"

# 프로덕션 서버 배포 (병렬)
deploy-prod: check-pem build-local
	@echo "📦 프로덕션 서버 배포 시작 (병렬)..."
	@if [ -z "$(SERVERS)" ]; then \
		echo "❌ SERVERS가 설정되지 않았습니다."; \
		echo "💡 사용법: make deploy-prod DEPLOY_SERVERS=\"ip1 ip2 ip3\""; \
		exit 1; \
	fi
	@echo "   Target servers: $(SERVERS)"
	@echo ""
	@pids=""; \
	for server in $(SERVERS); do \
		echo "  → Starting deployment to $$server..."; \
		($(deploy_single_server) && \
		 echo "  ✅ $$server deployment completed" || \
		 echo "  ❌ $$server deployment failed") & \
		pids="$$pids $$!"; \
	done; \
	echo ""; \
	echo "⏳ Waiting for all deployments to complete..."; \
	wait $$pids; \
	echo ""
	@echo "🎉 프로덕션 배포 완료!"

# 개발 서버 배포 (병렬)
deploy-dev: check-pem build-local
	@echo "📦 개발 서버 배포 시작 (병렬)..."
	@if [ -z "$(DEV_SERVER)" ]; then \
		echo "❌ DEV_SERVER가 설정되지 않았습니다."; \
		echo "💡 사용법: make deploy-dev DEV_SERVER=\"ip1 ip2 ip3\""; \
		exit 1; \
	fi
	@echo "   Target servers: $(DEV_SERVER)"
	@echo ""
	@pids=""; \
	for server in $(DEV_SERVER); do \
		echo "  → Starting deployment to $$server..."; \
		($(deploy_single_server) && \
		 echo "  ✅ $$server deployment completed" || \
		 echo "  ❌ $$server deployment failed") & \
		pids="$$pids $$!"; \
	done; \
	echo ""; \
	echo "⏳ Waiting for all deployments to complete..."; \
	wait $$pids; \
	echo ""
	@echo "🎉 개발 서버 배포 완료!"

# 모든 서버에 배포 (DEPLOY_SERVERS + DEV_SERVER, 병렬)
deploy-all: check-pem build-local
	@echo "📦 모든 서버에 배포 시작 (병렬)..."
	@all_servers="$(DEPLOY_SERVERS) $(DEV_SERVER)"; \
	if [ -z "$$all_servers" ]; then \
		echo "❌ 배포할 서버가 설정되지 않았습니다."; \
		echo "💡 DEPLOY_SERVERS 또는 DEV_SERVER를 설정하세요."; \
		exit 1; \
	fi; \
	echo "   Target servers: $$all_servers"; \
	echo ""; \
	pids=""; \
	for server in $$all_servers; do \
		echo "  → Starting deployment to $$server..."; \
		($(deploy_single_server) && \
		 echo "  ✅ $$server deployment completed" || \
		 echo "  ❌ $$server deployment failed") & \
		pids="$$pids $$!"; \
	done; \
	echo ""; \
	echo "⏳ Waiting for all deployments to complete..."; \
	wait $$pids; \
	echo ""
	@echo "🎉 모든 서버 배포 완료!"