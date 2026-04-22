export const uploadTagGroups = [
  {
    key: 'topic',
    label: '内容主题',
    tagNames: ['编程开发', '人工智能', '数码', '电竞', '校园', '健身', '穿搭', '探店', '财经', '心理学', '科普', '二次元', '阅读', '书评', '职场', '成长', '效率', '摄影', '家居', '宠物', '情感', '旅行']
  },
  {
    key: 'format',
    label: '内容形式',
    tagNames: ['教程', '实战', '入门', '进阶', '评测', '解说', '干货', '面试', '项目讲解', '复盘', '开箱', '剪辑', 'Vlog', '搞笑', '原理', '源码', '调优', '避坑', '盘点', '高光', '上手', '版本解析', '开荒']
  },
  {
    key: 'tech',
    label: '技术 / 工具',
    tagNames: ['Java', 'Python', 'Go', 'TypeScript', 'Node.js', 'SpringBoot', 'SpringCloud', 'Vue', 'React', 'MySQL', 'PostgreSQL', 'MongoDB', 'Redis', 'Docker', 'Linux', 'Git', 'Kafka', 'Nginx', 'Elasticsearch', 'Netty', 'Kubernetes', 'Maven', 'Gradle', 'RabbitMQ', 'DevOps', 'CI/CD']
  },
  {
    key: 'direction',
    label: '方向 / 场景',
    tagNames: ['前端', '后端', '算法', '机器学习', '系统设计', '高并发', '微服务', '分布式', '数据结构', '计算机网络', '操作系统']
  },
  {
    key: 'style',
    label: '平台内容 / 风格',
    tagNames: ['鬼畜', '翻唱', '影视解说', '篮球', '足球', '剧情', '集锦', '攻略', '混剪', '名场面', '高能', '催泪', '影评', '吐槽', '配音']
  }
]

export const defaultTagNames = ['教程', '实战', 'Vlog', '评测', '解说', '干货', '避坑', '盘点']

export const categoryTagMap = {
  '科技': ['编程开发', '人工智能', '数码', 'Java', 'Python', 'Go', 'TypeScript', 'Node.js', 'SpringBoot', 'Vue', 'React', 'MySQL', 'PostgreSQL', 'MongoDB', 'Redis', 'Docker', 'Kubernetes', '教程', '原理', '实战'],
  '科技 / 编程开发': ['编程开发', 'Java', 'Python', 'Go', 'TypeScript', 'Node.js', 'SpringBoot', 'SpringCloud', 'Vue', 'React', 'MySQL', 'PostgreSQL', 'MongoDB', 'Redis', 'Docker', 'Linux', 'Git', 'Kafka', 'Nginx', 'Elasticsearch', 'Netty', 'Kubernetes', 'Maven', 'Gradle', 'RabbitMQ', 'DevOps', 'CI/CD', '前端', '后端', '数据结构', '计算机网络', '操作系统', '系统设计', '高并发', '微服务', '分布式', '教程', '实战', '面试', '项目讲解', '原理', '源码', '调优', '避坑'],
  '科技 / 人工智能': ['人工智能', '机器学习', '算法', 'Python', 'Go', '教程', '实战', '干货', '原理', '复盘'],
  '科技 / 数码评测': ['数码', '开箱', '评测', '解说', '盘点', '高光'],
  '知识': ['科普', '心理学', '阅读', '书评', '职场', '成长', '效率', '干货', '复盘'],
  '知识 / 科普': ['科普', '干货', '解说', '复盘', '阅读'],
  '知识 / 学习方法': ['学习方法', '成长', '效率', '复盘', '干货'],
  '生活': ['校园', '健身', '穿搭', '摄影', '家居', '宠物', '情感', '搞笑'],
  '生活 / 日常': ['校园', '摄影', '成长', '情感', 'Vlog'],
  '影视 / 电影': ['剧情', '解说', '影视解说', '剪辑', '混剪', '影评', '吐槽', '配音', '名场面', '高能', '催泪'],
  '游戏 / 电竞': ['电竞', '攻略', '解说', '集锦', '高光', '版本解析', '开荒'],
  '美食': ['探店', '评测', 'Vlog', '摄影', '旅行'],
  '美食 / 探店': ['探店', '评测', 'Vlog', '摄影'],
  'Vlog': ['Vlog', '校园', '搞笑', '旅行', '摄影'],
  'Vlog / 出行': ['Vlog', '探店', '剪辑', '旅行', '摄影']
}
