# ============================================================
# 构建阶段：编译依赖 wheel
# 仅在此阶段保留 gcc / libpq-dev；若某个包无预编译 wheel 才会用到
# ============================================================
FROM python:3.11-slim AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /build

RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt


# ============================================================
# 运行阶段：仅保留运行时产物，不含编译工具链
# asyncpg / psycopg2-binary / numpy 均为预编译 wheel，无需系统 libpq
# ============================================================
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
# 以 bind mount 方式读取 builder 的 wheel 目录，避免 COPY 产生一份
# 无法回收的中间层（wheels 约 37MB，会永久留在镜像里）
RUN --mount=type=bind,from=builder,source=/wheels,target=/wheels \
    pip install --no-cache-dir --no-index --find-links=/wheels -r requirements.txt

COPY . .

# 以非 root 运行；data 目录需可写（SQLite 落盘位置）
RUN useradd --create-home --uid 1000 appuser \
    && mkdir -p /app/data \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

# 用 Python 自检，避免为 HEALTHCHECK 额外安装 curl
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]