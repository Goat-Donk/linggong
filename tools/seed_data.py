# -*- coding: utf-8 -*-
"""
本地零工平台 —— 种子数据脚本（可复用）

作用：把「内容好少」的测试库清空，重建一套贴近真实零工场景的演示数据，
      并同步预热 Redis（GEO / 报名名额 / 关注集合 / Feed 收件箱）。

数据规模：
  - 分类       6  （发传单/家教辅导/搬运工/促销导购/客服/保洁）
  - 用户       50 （雇主 14 + 打工人 36，含你本人账号 13581043338）
  - 岗位       40 （6 分类，北京真实坐标）
  - 报名记录   60 （雪花 id，状态 0待确认/1已录用/2已完成/3已取消 混合）
  - 互评       40 （已完成岗位的雇主⇄工人双向评价）
  - 关注       30
  - 动态       30

关键约定（与后端代码对齐）：
  - tb_job_application.id 用雪花算法：id = (create_time 秒 - 1640995200) << 32 | 当日序列号
  - GEO key：geo:job:{categoryId}；报名名额 key：apply:stock:{jobId}
  - 关注 key：follows:{userId}；Feed 收件箱 key：feed:{userId}（ZSet，score=动态时间戳毫秒）

用法：在项目根目录执行  python tools/seed_data.py
依赖：pip install pymysql redis
"""
import random
from datetime import datetime, timedelta

import pymysql
import redis

# ---------- 连接配置（与 docker-compose.yml / application.yml 一致） ----------
DB = dict(host="localhost", port=3307, user="root", password="123456",
          database="linggong", charset="utf8mb4")
REDIS = dict(host="localhost", port=6379, decode_responses=True)

# 与后端 RedisIdWorker.BEGIN_TIMESTAMP 一致（2022-01-01 00:00:00 UTC 秒数）
BEGIN_TIMESTAMP = 1640995200

# 固定随机种子，保证每次跑出来的数据可复现
random.seed(42)

NOW = datetime.now()


# ---------- 工具函数 ----------
def snowflake_id(dt, seq):
    """按后端雪花算法格式生成报名单号：高 32 位=相对时间戳秒，低 32 位=序列号。"""
    epoch_sec = int(dt.timestamp())
    return ((epoch_sec - BEGIN_TIMESTAMP) << 32) | seq


def phone_for(i):
    """按序号确定性生成唯一合法手机号（1[3-9]xxxxxxxxx），与用户真实号不冲突。"""
    prefixes = ["138", "139", "137", "150", "151", "152", "155", "156", "158", "159",
                "186", "187", "188", "133", "136", "173", "176", "177", "178", "180",
                "181", "182", "183", "184", "185", "189", "199", "198", "153"]
    p = prefixes[i % len(prefixes)]
    suffix = 10000000 + (i * 7919) % 90000000
    return f"{p}{suffix:08d}"


def fmt(dt):
    return dt.strftime("%Y-%m-%d %H:%M:%S")


# ---------- 数据：用户 ----------
# 雇主（role=1）：昵称 + 简介 + 年龄 + 性别(1男/2女)
EMPLOYERS = [
    ("陈志强", "开了十年装修公司，常年接散活，师傅不够就招零工", 43, 1),
    ("李红梅", "面馆老板娘，饭点忙不过来招钟点工，日结不拖欠", 38, 2),
    ("王建国", "培训机构负责人，寒暑假和周末招兼职辅导老师", 45, 1),
    ("张伟", "电商大促仓库主管，招分拣打包临时工", 34, 1),
    ("刘桂芳", "家政服务公司经理，招保洁小时工和月嫂，包培训", 41, 2),
    ("赵德柱", "会展搭建老板，布展撤展旺季招搬运工和杂工", 39, 1),
    ("孙丽", "开了两家淘宝店，招售前售后客服，可在家办公", 32, 2),
    ("周强", "连锁超市店长，周末和节假日招促销员", 36, 1),
    ("吴敏", "健身房前台主管，招地推发单员和前台，环境好", 29, 2),
    ("郑小龙", "写字楼物业经理，招日常保洁和绿化工", 44, 1),
    ("冯雪", "服装店老板，招周末导购和理货员", 33, 2),
    ("蒋涛", "酒吧老板，招夜班搬运和服务员，凌晨下班", 30, 1),
    ("韩梅", "摄影工作室主理人，招修图助理和活动摄影", 31, 2),
    ("曹磊", "搬家公司老板，常年招搬家师傅，多劳多得", 40, 1),
]

# 打工人（role=0）：昵称 + 简介 + 年龄 + 性别
WORKERS = [
    ("阿强", "白天跑外卖，晚上接搬运零活，力气大", 28, 1),
    ("小美", "全职宝妈，孩子上学后接半天保洁和促销", 33, 2),
    ("老赵", "下岗职工，会水电维修，找稳定零工", 51, 1),
    ("大刘", "工地散工，钢筋水电都行，随叫随到", 36, 1),
    ("小李同学", "大二学生，周末和寒暑假兼职家教", 20, 1),
    ("阿玲", "宝妈，会做饭，接家政和看护", 35, 2),
    ("阿辉", "退伍军人，身板硬朗，搬家和安保都行", 30, 1),
    ("小陈", "应届毕业生，找短期客服和文职过渡", 23, 1),
    ("老周", "会开叉车，有证，仓库搬运经验丰富", 47, 1),
    ("阿珍", "缝纫工出身，会改衣服，接手工零活", 42, 2),
    ("小芳", "大专在读，兼职促销导购，能说会道", 21, 2),
    ("老马", "退伍军人，会开车，接货运和搬家", 38, 1),
    ("小林", "兼职摄影师，接活动跟拍和修图", 26, 1),
    ("小何", "程序员裸辞，过渡期接代码和搬运零活", 29, 1),
    ("王师傅", "快递员，熟悉片区，接跑腿和搬运", 35, 1),
    ("刘骑手", "外卖骑手，午高峰晚高峰外接零活", 27, 1),
    ("陈保安", "做了五年保安，接夜班和活动安保", 45, 1),
    ("阿明", "厨师，接宴会帮厨和家常菜上门", 39, 1),
    ("老黄", "电工，有低压电工证，接维修安装", 50, 1),
    ("小李焊", "焊工，有证，接钢构和门窗焊接", 34, 1),
    ("设计师小张", "平面设计，接海报和详情页", 25, 2),
    ("翻译小刘", "英语专业，接翻译和家教", 24, 2),
    ("杨师傅", "有叉车证，仓库装卸一把好手", 41, 1),
    ("王阿姨", "保洁阿姨，手脚麻利，找小时工", 49, 2),
    ("刘月嫂", "金牌月嫂，有证，接月子和育婴", 44, 2),
    ("育婴师小周", "育婴师，带娃经验丰富，接日托", 32, 2),
    ("家教小张", "师范大学学生，接小学初中家教", 21, 2),
    ("英语老师兼职", "在职英语老师，周末接辅导", 30, 2),
    ("美术老师", "少儿美术老师，接写生和辅导", 28, 2),
    ("钢琴陪练", "音乐学院学生，接钢琴陪练", 22, 2),
    ("健身教练", "私教，接团课和陪练", 27, 1),
    ("瑜伽老师", "瑜伽教练，接私教和公司团课", 29, 2),
    ("网约车司机", "网约车司机，接接送和代驾", 40, 1),
    ("修车师傅", "修了八年车，接上门补胎和检修", 46, 1),
    ("木工师傅", "老木工，接家具安装和维修", 48, 1),
]

# 本人账号（保留登录号），昵称可自行在个人页修改
MY_PHONE = "13581043338"

# ---------- 数据：岗位（category, name, address, x, y, salary, headcount, description） ----------
# 分类 id：1发传单 2家教辅导 3搬运工 4促销导购 5客服 6保洁
JOBS = [
    # ---- 发传单 ----
    (1, "周末商场传单派发", "朝阳区望京SOHO塔3", 116.483, 39.996, 150, 6, "周末两天在望京商圈派发活动传单，日结150，早10点到晚6点，中间休息两小时。"),
    (1, "地铁口楼盘宣传单派发", "朝阳区大望路地铁站B口", 116.478, 39.907, 160, 4, "在地铁口及周边写字楼派发楼盘宣传单，要求勤快能站，穿统一马甲，日结160。"),
    (1, "培训机构地推发单", "海淀区中关村大街", 116.316, 39.982, 170, 5, "教培机构地推，在中关村商圈和学校周边发单引流，有到访提成，底薪加提成。"),
    (1, "社区超市开业传单", "丰台区科技园富丰路", 116.288, 39.831, 130, 8, "新开超市开业传单派发，负责周边小区扫楼，工作轻松，日结130，管饭。"),
    (1, "展会宣传单派发", "顺义区新国展", 116.556, 40.109, 180, 3, "展会三天派发宣传册和礼品袋，需要普通话流利，形象干净，日结180。"),
    (1, "亲子乐园传单派发", "石景山区万达广场", 116.221, 39.906, 140, 4, "周末在万达广场派发亲子乐园体验券，带娃家长多，好派，日结140。"),
    (1, "夜跑活动传单派发", "朝阳区奥林匹克森林公园南门", 116.398, 40.013, 150, 5, "傍晚在奥森公园派发夜跑活动传单，时间灵活，适合下班后来，日结150。"),
    # ---- 家教辅导 ----
    (2, "小学数学一对一家教", "海淀区五道口华清嘉园", 116.338, 39.992, 200, 2, "辅导四年级孩子数学，每周三次，每次两小时，200元/次，师范生优先。"),
    (2, "初中英语辅导", "朝阳区太阳宫", 116.443, 39.972, 250, 2, "初中生英语提分辅导，每周两次，要求英语六级以上，有耐心，250元/次。"),
    (2, "高中数学家教", "西城区德胜门", 116.375, 39.955, 300, 1, "高二数学一对一，孩子基础薄弱需要系统补，300元/次，每次两小时，重点院校优先。"),
    (2, "小学作业辅导托管", "海淀区知春路", 116.327, 39.976, 180, 3, "晚托班作业辅导，周一到周五下午4-7点，看管小学生写作业，180元/次。"),
    (2, "钢琴陪练", "朝阳区国贸CBD", 116.459, 39.908, 150, 2, "钢琴陪练，孩子练琴时陪练纠正，每周两次，150元/小时，音乐专业优先。"),
    (2, "初中物理家教", "通州区万达广场附近", 116.659, 39.908, 260, 2, "初三物理冲刺辅导，每周两次，260元/次，有中考辅导经验者优先。"),
    (2, "英语口语陪练", "海淀区西二旗", 116.305, 40.052, 220, 3, "成人英语口语陪练，每周两次，主要练日常和工作场景，220元/次，口语流利即可。"),
    # ---- 搬运工 ----
    (3, "搬家搬运工", "朝阳区三里屯", 116.455, 39.936, 300, 4, "搬家搬运，家具家电上下楼，按单结算，一单300起，力气大者优先，当天结。"),
    (3, "仓库卸货临时工", "大兴区亦庄经济开发区", 116.506, 39.795, 260, 6, "电商仓到货卸车，工作8小时，260元/天，管中饭，需体力好，能吃苦。"),
    (3, "建材搬运", "昌平区回龙观建材城", 116.343, 40.070, 280, 3, "建材城搬运水泥瓷砖等，280元/天，日结，长期有活，最好有搬运经验。"),
    (3, "家具搬运安装", "丰台区角门西", 116.373, 39.848, 320, 2, "家具搬运并简单组装，320元/天，需要会用电动螺丝刀，手脚麻利。"),
    (3, "快递分拣搬运", "顺义区空港物流园", 116.653, 40.130, 240, 8, "快递中转场分拣搬运，夜班为主，240元/班，工作8小时，可长期。"),
    (3, "展会布展搬运", "朝阳区国家会议中心", 116.403, 40.004, 300, 5, "展会布展撤展搬运，300元/天，管饭，加班另算，适合身板好的兄弟。"),
    (3, "冷库搬运工", "大兴区黄村", 116.341, 39.726, 350, 3, "冷库出货搬运，需要适应低温环境，350元/天，提供防寒服，日结。"),
    # ---- 促销导购 ----
    (4, "超市周末促销员", "朝阳区家乐福望京店", 116.472, 39.996, 200, 4, "周末两天超市饮料促销，试饮加介绍，200元/天，需要健康证，能说会道优先。"),
    (4, "商场化妆品导购", "西城区西单大悦城", 116.374, 39.913, 250, 3, "化妆品专柜导购，有提成，250元/天加提成，形象好，有导购经验优先。"),
    (4, "服装店导购", "东城区王府井步行街", 116.410, 39.915, 220, 3, "服装店周末导购，负责试衣和理货，220元/天，年轻有亲和力即可。"),
    (4, "饮料试饮促销", "海淀区五道口购物中心", 116.338, 39.992, 190, 4, "新饮品试饮推广，周末两天，190元/天，性格开朗，敢于主动邀请顾客。"),
    (4, "家电促销导购", "朝阳区国美电器", 116.447, 39.921, 260, 2, "家电卖场促销，介绍冰箱洗衣机，260元/天加提成，有家电销售经验优先。"),
    (4, "生鲜试吃导购", "朝阳区盒马鲜生", 116.461, 39.925, 210, 3, "生鲜区试吃推广，切水果分装，210元/天，爱干净，有健康证优先。"),
    (4, "母婴用品导购", "朝阳区蓝色港湾", 116.487, 39.951, 230, 2, "母婴店导购，介绍奶粉纸尿裤，230元/天，宝妈或有育儿经验者优先。"),
    # ---- 客服 ----
    (5, "电商售前客服", "海淀区中关村软件园", 116.298, 40.051, 180, 3, "淘宝店售前客服，早班9-18点，回复咨询促成下单，180元/天，打字快即可。"),
    (5, "电话回访客服", "朝阳区劲松", 116.460, 39.885, 190, 4, "电话回访老客户，普通话标准，190元/天，有固定话术，简单易上手。"),
    (5, "在线客服兼职", "远程办公", 116.400, 39.900, 170, 5, "在线客服，处理售后咨询，可远程在家办公，170元/天，需电脑和稳定网络。"),
    (5, "投诉处理客服", "丰台区丽泽商务区", 116.328, 39.866, 210, 2, "投诉处理专员，安抚客户情绪，210元/天，需要有耐心，有客服经验优先。"),
    (5, "App客服专员", "海淀区上地", 116.303, 40.037, 200, 3, "App内客服，解答使用问题，200元/天，会基本电脑操作即可，可长期。"),
    (5, "售后催单客服", "通州区万达写字楼", 116.659, 39.908, 185, 4, "售后催单和物流跟进，185元/天，工作环境好，有电脑基础即可。"),
    # ---- 保洁 ----
    (6, "家庭保洁小时工", "朝阳区望京各小区", 116.483, 39.996, 50, 6, "家庭日常保洁，按小时计费50元/小时，每次3小时起，工具齐全，长期有单。"),
    (6, "办公室日常保洁", "朝阳区国贸写字楼", 116.459, 39.908, 220, 3, "写字楼办公室日常保洁，周一至周五，220元/天，早上7点到下午3点。"),
    (6, "开荒保洁", "大兴区新交付楼盘", 116.341, 39.726, 300, 4, "新房开荒保洁，300元/天，需要有力气能爬高，提供工具，日结。"),
    (6, "商场夜班保洁", "西城区金融街购物中心", 116.360, 39.914, 240, 3, "商场闭店后夜班保洁，240元/班，晚上10点到凌晨6点，适合能上夜班的。"),
    (6, "写字楼日常保洁", "海淀区中关村大厦", 116.316, 39.982, 210, 3, "写字楼公共区域日常保洁，210元/天，早班，有保洁经验优先。"),
    (6, "月子中心保洁", "朝阳区亚运村", 116.406, 39.985, 260, 2, "月子中心客房保洁，要求细心爱干净，260元/天，提供工作餐，女性优先。"),
]

# ---------- 数据：动态内容（打工人晒单） ----------
BLOG_CONTENTS = [
    "今天去望京发了一天传单，日结150到手，老板人挺爽快，就是站了一天腿有点酸。",
    "第一次接搬家的活，帮一家三口搬东西，三个人忙活一下午，一人分了300，累并快乐着。",
    "周末给个初中生补英语，孩子进步挺快，家长还多给了50，说下周末继续。",
    "做了一天超市促销，试饮的酸奶卖出去三十多箱，提成到手，美滋滋。",
    "接了个开荒保洁，新房灰大，干完看着干干净净的房子特有成就感。",
    "冷库搬了一晚上货，工资挺高就是冻得慌，下次记得多穿点。",
    "兼职客服第一天，在家办公爽，就是咨询的人太多，打字打到手软。",
    "会展布展干了三天，管饭加班费另算，攒了快一千，可以歇几天了。",
    "给小学生辅导作业，熊孩子太皮，不过家长人很好，耐心点慢慢来。",
    "商场导购第一天，卖出去五件衣服，店长说我挺适合这行，考虑转长期。",
    "夜班保洁适应了，白天补觉，晚上上班，时间自由也挺好。",
    "钢琴陪练这活不错，一小时150，还蹭了人家一架好琴练手。",
    "快递分拣夜班，累是真的累，但日结工资当天到账，踏实。",
    "家具安装，用电动螺丝刀装了三个衣柜一个床，客户很满意，下次还找我。",
    "电话回访客服，一上午打了几十个电话，嗓子冒烟，不过钱好挣。",
    "帮人搬家，上下楼跑了十几趟，晚上回家直接躺平，值了。",
    "母婴店导购，给宝妈推荐奶粉，大家都很信任我，有成就感。",
    "英语口语陪练，跟客户聊了一个小时，自己的口语都进步了。",
    "奥森公园夜跑活动发单，环境好心情好，还白嫖了一场夜跑。",
    "电商仓卸车，一车货几十箱，哥几个配合默契，半天就干完了。",
    "周末家教连排三节课，嗓子有点哑，但看到孩子成绩提升就值。",
    "试吃促销，自己先尝了个够，还卖出不少，这活可以常干。",
    "写字楼保洁，楼层高风景好，边擦玻璃边看北京城，也挺惬意。",
    "投诉处理客服，遇到难缠的客户要耐心，学会了不生气，收获很大。",
    "建材搬运，水泥真沉，老师傅教我用巧劲，省力不少。",
    "陪练口语认识了个做外贸的朋友，还给我介绍了个翻译的活。",
    "盒马试吃，切水果切到手软，顾客都夸我摆盘好看。",
    "装了个复杂的书架，研究图纸半天，最后严丝合缝，强迫症舒服了。",
    "夜班保安兼职，站岗巡逻，安静的时候还能听会儿书。",
    "上门修了个水管，顺手把漏水的龙头也换了，阿姨非要留我吃饭。",
]

# ---------- 数据：评价内容 ----------
EVAL_CONTENTS = [
    "活儿干得利索，人也好沟通，下次有活还找你。",
    "很守时，提前就到了，做得也认真，好评。",
    "沟通顺畅，干活动作麻利，推荐。",
    "态度好，活儿细，值得信赖。",
    "老板人爽快，工资当天就结了，靠谱。",
    "要求明确，不拖泥带水，合作愉快。",
    "挺负责的，遇到问题主动解决，点赞。",
    "效率高，效果超出预期，满意。",
    "人很实在，价钱也公道。",
    "配合默契，下次有需要优先考虑。",
]


# ---------- 数据库与 Redis 连接 ----------
def main():
    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    r = redis.Redis(**REDIS)

    # ---- 1. 清空所有表（业务表全部重建，TRUNCATE 同时重置自增） ----
    tables = ["tb_blog", "tb_follow", "tb_job_evaluation", "tb_job_application",
              "tb_job", "tb_user_info", "tb_user", "tb_job_category"]
    cur.execute("SET FOREIGN_KEY_CHECKS=0")
    for t in tables:
        cur.execute(f"TRUNCATE TABLE `{t}`")
    cur.execute("SET FOREIGN_KEY_CHECKS=1")
    conn.commit()

    # ---- 2. 分类 ----
    categories = ["发传单", "家教辅导", "搬运工", "促销导购", "客服", "保洁"]
    cat_ids = {}
    for i, name in enumerate(categories, start=1):
        cur.execute("INSERT INTO tb_job_category(name, sort) VALUES(%s, %s)", (name, i))
        cat_ids[i] = i  # TRUNCATE 后自增从 1 开始，id 与分类序号一致

    # ---- 3. 用户 + 用户资料 ----
    employer_ids = []
    worker_ids = []
    phones_seen = set()
    phone_seq = 0

    def insert_user(nick, role, introduce, age, gender, phone=None):
        nonlocal phone_seq
        if phone is None:
            while True:
                phone = phone_for(phone_seq)
                phone_seq += 1
                if phone not in phones_seen:
                    break
        assert phone not in phones_seen, f"手机号重复：{phone}"
        phones_seen.add(phone)
        cur.execute(
            "INSERT INTO tb_user(phone, nick_name, icon, role, create_time) "
            "VALUES(%s, %s, '', %s, %s)", (phone, nick, role, fmt(NOW)))
        uid = cur.lastrowid
        cur.execute(
            "INSERT INTO tb_user_info(user_id, introduce, age, gender, credit) "
            "VALUES(%s, %s, %s, %s, %s)",
            (uid, introduce, age, gender, random.choice([100, 100, 100, 98, 96])))
        return uid

    for nick, intro, age, gender in EMPLOYERS:
        employer_ids.append(insert_user(nick, 1, intro, age, gender))
    for nick, intro, age, gender in WORKERS:
        worker_ids.append(insert_user(nick, 0, intro, age, gender))
    # 本人账号：打工人身份，昵称可改
    my_id = insert_user("阿禾", 0, "零工平台用户，什么活都能干点", 26, 1, phone=MY_PHONE)
    worker_ids.append(my_id)

    # ---- 4. 岗位 ----
    job_rows = []  # (id, category_id, employer_id, status, start_time)
    down_cats = set()  # 每个分类下架 1 个岗位（历史已完成），避免集中在某个分类
    for idx, (cat, name, addr, x, y, salary, hc, desc) in enumerate(JOBS):
        employer_id = employer_ids[idx % len(employer_ids)]
        # 每个分类的第一个岗位设成已下架（历史已完成），其余上架
        is_down = cat not in down_cats
        if is_down:
            down_cats.add(cat)
        status = 1 if is_down else 0
        if is_down:
            start = NOW - timedelta(days=random.randint(10, 20))
        else:
            start = NOW + timedelta(days=random.randint(0, 9))
        end = start + timedelta(days=random.randint(1, 3))
        cur.execute(
            "INSERT INTO tb_job(category_id, employer_id, name, address, x, y, salary, "
            "headcount, start_time, end_time, description, status, create_time) "
            "VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (cat, employer_id, name, addr, x, y, salary, hc, fmt(start), fmt(end), desc, status,
             fmt(NOW - timedelta(days=random.randint(1, 25)))))
        job_rows.append((cur.lastrowid, cat, employer_id, status, start))

    conn.commit()

    # ---- 5. 报名记录（雪花 id）+ 互评 ----
    app_rows = []  # (id, job_id, worker_id, status, create_time)
    eval_count = 0
    day_seq = {}
    _app_workers = set()  # (job_id, worker_id) 去重

    for (job_id, cat, employer_id, job_status, job_start) in job_rows:
        # 每个岗位 0~4 个报名，打工人里挑（跳过雇主）
        n = random.randint(0, 4)
        picked = random.sample(worker_ids, min(n, len(worker_ids)))
        for worker_id in picked:
            if (job_id, worker_id) in _app_workers:
                continue
            _app_workers.add((job_id, worker_id))
            # 已下架的历史岗位 → 已完成；上架岗位 → 混合状态
            if job_status == 1:
                status = 2
            else:
                status = random.choices([0, 1, 2, 3], weights=[40, 35, 15, 10])[0]
            ct = NOW - timedelta(days=random.randint(0, 14),
                                 hours=random.randint(0, 23), minutes=random.randint(0, 59))
            day = ct.strftime("%Y%m%d")
            day_seq[day] = day_seq.get(day, 0) + 1
            app_id = snowflake_id(ct, day_seq[day])
            cur.execute(
                "INSERT INTO tb_job_application(id, job_id, worker_id, status, create_time, update_time) "
                "VALUES(%s,%s,%s,%s,%s,%s)",
                (app_id, job_id, worker_id, status, fmt(ct), fmt(ct)))
            app_rows.append((app_id, job_id, worker_id, status, ct))

            # 已完成 → 生成互评（雇主评工人 + 工人评雇主）
            if status == 2:
                ev = random.choice(EVAL_CONTENTS)
                cur.execute(
                    "INSERT INTO tb_job_evaluation(job_id, from_user_id, to_user_id, rating, content, create_time) "
                    "VALUES(%s,%s,%s,%s,%s,%s)",
                    (job_id, employer_id, worker_id, random.randint(4, 5), ev, fmt(ct)))
                eval_count += 1
                if random.random() < 0.8:  # 八成双向互评
                    ev2 = random.choice(EVAL_CONTENTS)
                    cur.execute(
                        "INSERT INTO tb_job_evaluation(job_id, from_user_id, to_user_id, rating, content, create_time) "
                        "VALUES(%s,%s,%s,%s,%s,%s)",
                        (job_id, worker_id, employer_id, random.randint(4, 5), ev2, fmt(ct)))
                    eval_count += 1

    conn.commit()

    # ---- 6. 关注关系（打工人关注雇主/其他打工人） ----
    follow_pairs = set()
    # 本人账号关注几个活跃打工人，保证登录后 Feed 有内容
    active_bloggers = worker_ids[:12]
    for wid in active_bloggers[:5]:
        follow_pairs.add((my_id, wid))
    # 打工人普遍关注 1~2 个雇主 + 1~2 个其他打工人
    for i, wid in enumerate(worker_ids):
        if i % 3 == 0:
            follow_pairs.add((wid, employer_ids[i % len(employer_ids)]))
        if i % 4 == 0 and len(worker_ids) > i + 1:
            follow_pairs.add((wid, worker_ids[(i + 1) % len(worker_ids)]))
    for uid, fuid in follow_pairs:
        if uid == fuid:
            continue
        cur.execute("INSERT IGNORE INTO tb_follow(user_id, follow_user_id) VALUES(%s, %s)",
                    (uid, fuid))
    conn.commit()

    # ---- 7. 动态 ----
    blog_rows = []  # (id, user_id, create_time)
    for i, content in enumerate(BLOG_CONTENTS):
        author = worker_ids[i % len(worker_ids)]
        ct = NOW - timedelta(days=random.randint(0, 20), hours=random.randint(0, 23),
                             minutes=random.randint(0, 59))
        cur.execute(
            "INSERT INTO tb_blog(user_id, title, content, images, liked, create_time) "
            "VALUES(%s, '', %s, '', %s, %s)",
            (author, content, random.randint(0, 30), fmt(ct)))
        blog_rows.append((cur.lastrowid, author, ct))
    conn.commit()

    # ---- 8. 预热 Redis ----
    r.flushdb()
    # 8.1 GEO（附近搜索）：geo:job:{categoryId}，member=岗位 id，坐标=(x,y)
    job_coord = {}
    for (job_id, cat, employer_id, status, start) in job_rows:
        cur.execute("SELECT x, y, headcount, status FROM tb_job WHERE id=%s", (job_id,))
        x, y, hc, st = cur.fetchone()
        job_coord[job_id] = (cat, x, y, hc, st)
        if st == 0 and x is not None and y is not None:
            r.geoadd(f"geo:job:{cat}", (x, y, str(job_id)))
    # 8.2 报名名额：apply:stock:{jobId} = headcount - 已占名额
    cur.execute("SELECT job_id, COUNT(*) FROM tb_job_application WHERE status IN (0,1) GROUP BY job_id")
    occupied = dict(cur.fetchall())
    for job_id, (cat, x, y, hc, st) in job_coord.items():
        if st == 0:
            remain = max(hc - occupied.get(job_id, 0), 1)
            r.set(f"apply:stock:{job_id}", str(remain))
    # 8.3 关注集合：follows:{userId}
    for uid, fuid in follow_pairs:
        if uid != fuid:
            r.sadd(f"follows:{uid}", str(fuid))
    # 8.4 Feed 收件箱：把每个用户关注的人最近 3 条动态滚入 feed:{userId}
    blog_by_author = {}
    for (bid, author, ct) in blog_rows:
        blog_by_author.setdefault(author, []).append((bid, ct))
    for uid, fuid in follow_pairs:
        if uid == fuid:
            continue
        recent = sorted(blog_by_author.get(fuid, []), key=lambda x: x[1], reverse=True)[:3]
        for bid, ct in recent:
            r.zadd(f"feed:{uid}", {str(bid): int(ct.timestamp() * 1000)})

    conn.close()

    # ---- 汇总 ----
    print("=" * 50)
    print("种子数据写入完成")
    print(f"  分类     : {len(categories)}")
    print(f"  用户     : {len(employer_ids)} 雇主 + {len(worker_ids)} 打工人")
    print(f"  岗位     : {len(job_rows)}（其中下架 {sum(1 for j in job_rows if j[3] == 1)}）")
    print(f"  报名记录 : {len(app_rows)}")
    print(f"  互评     : {eval_count}")
    print(f"  关注     : {len(follow_pairs)}")
    print(f"  动态     : {len(blog_rows)}")
    print(f"  Redis    : GEO 岗位 {sum(1 for j in job_coord.values() if j[4] == 0)}，"
          f"名额 {sum(1 for j in job_coord.values() if j[4] == 0)}，"
          f"关注集合 {len([1 for u, f in follow_pairs if u != f])}")
    print("=" * 50)
    print("提示：")
    print(f"  - 本人账号 {MY_PHONE}（打工人），昵称「阿禾」，可在个人页改。")
    print(f"  - 雇主演示账号（登录后验证码看后端日志）：")
    for i, eid in enumerate(employer_ids[:3]):
        print(f"      {EMPLOYERS[i][0]}（role=雇主）")
    print("  - 请重启后端，让布隆过滤器从 DB 重新预载岗位 id（否则岗位详情报「岗位不存在」）。")


if __name__ == "__main__":
    main()
