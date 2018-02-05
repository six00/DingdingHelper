# DingdingHelper

修改自https://github.com/Justson/DingDingHelper 的项目  在此非常感谢

1.由于自己打卡使用的手机性能较差 所以在多处容易出错的地方增加了延时

2.根据自己的屏幕修改部分无法直接通过AccessibilityService获取到的页面打开方式 比如考勤页面（使用adb模拟点击 可自行修改services\DingDingHelperAccessibilityService中openCheckInPager方法中目标点的参数）

3.由于页面设置打卡时间是固定的，整个星期时间都一样太不合情理，自己每天设置又太麻烦，故增加打卡完成后自动随机设置下次打卡时间功能（原代码默认为一周上班6天，可自行修改utils\DingHelperUtils中35行代码的循环次数为5天打卡）

4.当然还有最重要的将主界面更换为英文显示，原先的字眼太显眼，为防止被敌军发现

以上

! [layout] (https://github.com/six00/DingdingHelper/blob/master/layout.png)
