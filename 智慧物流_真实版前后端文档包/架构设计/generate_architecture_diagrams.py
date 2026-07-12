#!/usr/bin/env python3
from pathlib import Path
from xml.sax.saxutils import escape

BASE = Path(__file__).resolve().parent


def text_lines(label: str, max_len: int = 18) -> list[str]:
    parts: list[str] = []
    buf = ""
    for ch in label:
        if len(buf) >= max_len and ch in " /-_，,、":
            parts.append(buf.strip(" /-_，,、"))
            buf = ""
            continue
        buf += ch
        if len(buf) >= max_len and ord(ch) < 128:
            parts.append(buf.strip())
            buf = ""
    if buf.strip():
        parts.append(buf.strip())
    return parts or [label]


class Svg:
    def __init__(self, width: int, height: int):
        self.width = width
        self.height = height
        self.items: list[str] = []
        self.defs = """
  <defs>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="strokeWidth">
      <path d="M 0 0 L 10 5 L 0 10 z" fill="#334155"/>
    </marker>
    <filter id="softShadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="6" stdDeviation="8" flood-color="#0f172a" flood-opacity="0.10"/>
    </filter>
  </defs>
"""

    def add(self, raw: str) -> None:
        self.items.append(raw)

    def text(self, x: int, y: int, label: str, size: int = 22, weight: int = 500,
             color: str = "#0f172a", anchor: str = "middle") -> None:
        self.add(
            f'<text x="{x}" y="{y}" text-anchor="{anchor}" '
            f'font-family="Noto Sans SC, Microsoft YaHei, Arial, sans-serif" '
            f'font-size="{size}" font-weight="{weight}" fill="{color}">{escape(label)}</text>'
        )

    def box(self, x: int, y: int, w: int, h: int, title: str, subtitle: str = "",
            fill: str = "#ffffff", stroke: str = "#4f46e5", icon: str | None = None,
            title_size: int = 22, subtitle_size: int = 15, radius: int = 12,
            icon_size: int = 58) -> None:
        self.add(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="2" filter="url(#softShadow)"/>'
        )
        icon_shift = 0
        if icon:
            self.add(
                f'<image href="{icon}" x="{x + 22}" y="{y + (h - icon_size) // 2}" '
                f'width="{icon_size}" height="{icon_size}" preserveAspectRatio="xMidYMid meet"/>'
            )
            icon_shift = icon_size // 2
        cy = y + 34
        for i, line in enumerate(text_lines(title, 20)):
            self.text(x + w // 2 + icon_shift // 2, cy + i * (title_size + 4), line, title_size, 650)
        if subtitle:
            sub_y = y + h - 34 if h < 100 else y + h - 44
            for i, line in enumerate(text_lines(subtitle, 24)[:2]):
                self.text(x + w // 2, sub_y + i * 18, line, subtitle_size, 400, "#475569")

    def group(self, x: int, y: int, w: int, h: int, title: str, fill: str = "#f8fafc",
              stroke: str = "#94a3b8") -> None:
        self.add(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="18" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="2" stroke-dasharray="8 8"/>'
        )
        self.text(x + 24, y + 34, title, 21, 700, "#334155", "start")

    def arrow(self, x1: int, y1: int, x2: int, y2: int, label: str = "",
              color: str = "#334155", dash: bool = False) -> None:
        dash_attr = ' stroke-dasharray="8 8"' if dash else ""
        self.add(
            f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="2.5" marker-end="url(#arrow)"{dash_attr}/>'
        )
        if label:
            self.add(f'<rect x="{(x1+x2)//2 - 78}" y="{(y1+y2)//2 - 30}" width="156" height="28" rx="14" fill="#ffffff" opacity="0.92"/>')
            self.text((x1 + x2) // 2, (y1 + y2) // 2 - 10, label, 15, 500, "#334155")

    def path_arrow(self, d: str, label: str = "", label_xy: tuple[int, int] | None = None,
                   color: str = "#334155", dash: bool = False) -> None:
        dash_attr = ' stroke-dasharray="8 8"' if dash else ""
        self.add(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="2.5" marker-end="url(#arrow)"{dash_attr}/>')
        if label and label_xy:
            x, y = label_xy
            self.add(f'<rect x="{x - 82}" y="{y - 22}" width="164" height="28" rx="14" fill="#ffffff" opacity="0.92"/>')
            self.text(x, y - 2, label, 15, 500, "#334155")

    def render(self) -> str:
        return (
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" '
            f'viewBox="0 0 {self.width} {self.height}">\n'
            f'{self.defs}\n'
            f'<rect width="{self.width}" height="{self.height}" fill="#f6f8fb"/>\n'
            + "\n".join(self.items)
            + "\n</svg>\n"
        )


def build_system_architecture() -> str:
    s = Svg(2200, 1280)
    s.text(1100, 66, "智慧物流 IoT 平台系统架构草稿", 42, 800)
    s.text(1100, 104, "主链路清晰表达：前端运营台 → 后端业务 API → 实时设备流 → 数据与外部智能能力", 21, 400, "#64748b")

    s.group(70, 150, 300, 450, "用户与客户端", "#ffffff", "#cbd5e1")
    s.box(110, 220, 220, 86, "调度员 / 仓管", "Web 运营台", "#f8fafc", "#94a3b8", title_size=22)
    s.box(110, 350, 220, 86, "货主 / 管理员", "查询与管理", "#f8fafc", "#94a3b8", title_size=22)
    s.box(110, 480, 220, 86, "司机 / 车载终端", "GPS / 心跳 / 回执", "#f8fafc", "#94a3b8", title_size=22)

    s.group(450, 150, 350, 450, "接入层", "#eef6ff", "#60a5fa")
    s.box(500, 230, 250, 106, "Nginx", "静态资源 / 反向代理", "#ffffff", "#60a5fa",
          "../../Icons/nginx.png", title_size=25, subtitle_size=16, icon_size=76)
    s.box(500, 402, 250, 106, "HTTP / WebSocket", "/api/v1 / ws", "#ffffff", "#60a5fa",
          None, title_size=23, subtitle_size=16)

    s.group(880, 150, 430, 450, "前端应用层", "#f0fdf4", "#22c55e")
    s.box(930, 230, 330, 106, "Vue 3 + Vite", "运营总览 / 追踪 / 调度 / 告警", "#ffffff", "#22c55e",
          None, title_size=25, subtitle_size=16)
    s.box(930, 402, 330, 106, "Voice Action 执行器", "跳转 / 地图定位 / 确认弹窗", "#ffffff", "#22c55e",
          None, title_size=24, subtitle_size=16)

    s.group(1390, 150, 740, 450, "后端应用层：Spring Boot 模块化单体", "#fff7ed", "#f97316")
    s.box(1438, 222, 240, 118, "Spring Boot Core", "Controller / Service / Mapper", "#ffffff", "#fb923c",
          "../../Icons/Spring.svg", title_size=23, subtitle_size=15, radius=12, icon_size=78)
    modules = [
        ("认证与用户", "/auth / users", 1728, 218),
        ("车辆货物", "/vehicles / cargo", 1910, 218),
        ("路线地图", "/routes / amap", 1728, 344),
        ("告警调度", "/alerts / command", 1910, 344),
        ("RAG 知识库", "/assistant / rag", 1438, 410),
        ("语音 Agent", "/voice / agent", 1606, 410),
    ]
    for title, sub, x, y in modules:
        s.box(x, y, 146, 88, title, sub, "#ffffff", "#fb923c", title_size=18, subtitle_size=13, radius=10)

    s.group(450, 690, 680, 330, "实时设备链路", "#ecfeff", "#06b6d4")
    s.box(510, 780, 170, 118, "EMQX", "MQTT Broker", "#ffffff", "#06b6d4",
          "../../Icons/emqx.png", 22, 15, 12, icon_size=76)
    s.box(732, 780, 170, 118, "Kafka", "gps / alert / cmd", "#ffffff", "#06b6d4",
          "../../Icons/kafka.png", 22, 15, 12, icon_size=76)
    s.box(954, 780, 120, 118, "WS", "实时推送", "#ffffff", "#06b6d4",
          None, 24, 15, 12)

    s.group(1210, 690, 920, 330, "数据与对象存储", "#f8fafc", "#64748b")
    data = [
        ("PostgreSQL", "业务数据", "../../Icons/postgres.png", 1260, 780),
        ("TimescaleDB", "时序 GPS", "../../Icons/timescale.svg", 1480, 780),
        ("Redis", "最新状态 / 去重", "../../Icons/redis.png", 1700, 780),
        ("MinIO", "附件 / 知识库", "../../Icons/minio-seeklogo.png", 1920, 780),
    ]
    for title, sub, icon, x, y in data:
        s.box(x, y, 170, 124, title, sub, "#ffffff", "#94a3b8", icon, 19, 14, 12, icon_size=72)

    s.group(70, 1082, 2060, 134, "外部能力与本地基础设施", "#ffffff", "#cbd5e1")
    external = [
        (132, "高德地图", "路线规划 / 地理编码 / 偏航辅助", None),
        (546, "百度语音 / 人脸", "ASR / 人脸登录", None),
        (960, "DeepSeek", "意图解析 / RAG 回答", None),
        (1374, "Docker Compose", "PostgreSQL / Redis / Kafka / EMQX / MinIO", "../../Icons/docker.png"),
    ]
    for x, title, sub, icon in external:
        s.box(x, 1124, 340, 60, title, sub, "#f8fafc", "#cbd5e1", icon, 19, 13, 8, icon_size=42)

    s.arrow(330, 262, 500, 282, "访问")
    s.arrow(330, 522, 510, 840, "MQTT 上报")
    s.arrow(750, 282, 930, 282, "页面")
    s.arrow(1260, 282, 1438, 282, "API")
    s.arrow(1910, 432, 902, 840, "指令下发")
    s.arrow(680, 840, 732, 840, "入流")
    s.arrow(902, 840, 954, 840, "消费")
    s.arrow(1074, 840, 1260, 840, "推送/保存")
    s.path_arrow("M 1560 340 C 1440 554, 1360 650, 1345 780", "业务落库", (1406, 642))
    s.path_arrow("M 1850 432 C 1790 600, 1784 690, 1785 780", "缓存状态", (1816, 648))
    s.path_arrow("M 1520 498 C 1750 610, 1940 648, 2005 780", "文档对象", (1848, 656))
    s.path_arrow("M 1900 262 C 2040 484, 1988 952, 1114 1124", "AI / 地图调用", (1908, 1042), dash=True)
    s.path_arrow("M 1095 454 C 1062 680, 1064 962, 960 1124", "语音 action", (1024, 1036), dash=True)
    return s.render()


def build_layered_design() -> str:
    s = Svg(1520, 820)
    s.text(760, 56, "智慧物流 IoT 平台顶层技术选型设计", 34, 800)
    s.text(760, 88, "参考 AZAZ 顶层模块图：按层表达技术边界，避免跨层箭头堆叠", 17, 400, "#64748b")

    rows = [
        ("用户层", 126, "#eef2ff", "#6366f1", ["货主端", "调度员端", "仓管端", "司机端", "管理员端"]),
        ("接入层", 236, "#eff6ff", "#3b82f6", ["Nginx", "HTTP API", "WebSocket", "JWT 鉴权", "Vite Proxy"]),
        ("前端层", 346, "#f0fdf4", "#22c55e", ["Vue 3", "Vite", "Pinia", "Element Plus", "Leaflet / 高德 JS", "VoiceAssistant"]),
        ("应用层", 470, "#fff7ed", "#f97316", ["Spring Boot", "认证用户", "车辆货物", "路线地图", "告警调度", "设备在线", "RAG / Agent"]),
        ("实时层", 594, "#ecfeff", "#06b6d4", ["EMQX", "MQTT Consumer", "Kafka", "定时检测任务", "WebSocket Push"]),
        ("数据层", 704, "#f8fafc", "#64748b", ["PostgreSQL", "TimescaleDB", "Redis", "MinIO", "pgvector"]),
    ]
    for label, y, fill, stroke, items in rows:
        s.text(80, y + 50, label, 26, 700, "#0f172a", "start")
        s.add(f'<rect x="170" y="{y}" width="1120" height="86" rx="14" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
        gap = 22
        item_w = int((1120 - gap * (len(items) + 1)) / len(items))
        x = 170 + gap
        for item in items:
            s.add(f'<rect x="{x}" y="{y + 22}" width="{item_w}" height="42" rx="8" fill="#ffffff" stroke="{stroke}" stroke-width="2"/>')
            s.text(x + item_w // 2, y + 49, item, 16, 550)
            x += item_w + gap

    s.add('<rect x="1348" y="126" width="118" height="664" rx="14" fill="#ffffff" stroke="#0f172a" stroke-width="2"/>')
    s.text(1407, 166, "工具", 28, 700)
    for y, item in [(218, "Git"), (300, "Knife4j"), (382, "Maven"), (464, "Docker"), (546, "MQTTX"), (628, "DataGrip"), (710, "Apifox")]:
        s.add(f'<rect x="1367" y="{y}" width="80" height="42" rx="7" fill="#fafafa" stroke="#8b5cf6" stroke-width="2"/>')
        s.text(1407, y + 27, item, 15, 550)

    for y1, y2, label in [(212, 236, "访问入口"), (322, 346, "页面运行"), (432, 470, "接口调用"),
                          (556, 594, "设备事件"), (680, 704, "持久化")]:
        s.arrow(730, y1, 730, y2, label)

    s.text(760, 794, "说明：当前实现为 Spring Boot 模块化单体；图中应用层模块是代码边界，不强行表达为微服务。", 16, 500, "#64748b")
    return s.render()


def main() -> None:
    outputs = {
        "智慧物流平台架构图草稿.svg": build_system_architecture(),
        "顶层设计分层图.svg": build_layered_design(),
    }
    for name, content in outputs.items():
        (BASE / name).write_text(content, encoding="utf-8")
        print(f"generated {BASE / name}")


if __name__ == "__main__":
    main()
