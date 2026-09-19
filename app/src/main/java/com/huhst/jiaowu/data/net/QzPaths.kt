package com.huhst.jiaowu.data.net

/**
 * 强智教务管理系统（jsxsd）的接口路径。
 *
 * ⚠️ 与「统一身份认证（正方 CAS）」是**两台不同内网主机上的两个独立系统**，
 * 因此 WebVPN 宿主码不同，需要分别处理。
 *
 * 已确认（用户实测提供）：
 *  - 教务系统宿主码 `…a1a70fcd696126012a51`
 *  - 主页路径 `/jsxsd/framework/xsMain.htmlx`（注意后缀是 .htmlx，不是 .jsp）
 *
 * 其余路径来自强智标准版的常见结构，**尚未逐条验证**。
 */
object QzPaths {

    /** 教务系统根路径。内网实测入口：http://10.1.1.149/jsxsd */
    const val ROOT = "/jsxsd"

    /** 登录后主页（用户在浏览器里实测到的地址）。 */
    const val HOME = "$ROOT/framework/xsMain.htmlx"

    /** 真正承载内容的主框架（主页 frameset 里的 Frame0）。 */
    const val MAIN_FRAME = "$ROOT/framework/xsMain_new.htmlx?t1=1"

    /**
     * 教务系统在 WebVPN 中的宿主码（用户提供）。
     *
     * 正常情况下登录后会自动发现并写入设置；这里作为**兜底默认值**，
     * 让「第一步成功后直接深链到教务系统」在尚未发现时也能工作。
     */
    const val JSXSD_HOST_CODE = "77726476706e69737468656265737421a1a70fcd696126012a51"

    /** 统一身份认证（正方 CAS）在 WebVPN 中的宿主码（先前实测得到）。 */
    const val CAS_HOST_CODE = "77726476706e69737468656265737421a1a70fcd6961260728"

    /** 教务系统登录页候选。强智各版本差异较大，登录后按实际落地页判定。 */
    val LOGIN_CANDIDATES = listOf(
        "$ROOT/",
        "$ROOT/framework/xsMain.htmlx",
        "$ROOT/xk/LoginToXk",
    )

    /** 学生课表 */
    const val SCHEDULE = "$ROOT/xskb/xskb_list.do"

    /** 个人信息 / 学籍 */
    const val PROFILE = "$ROOT/grxx/xsxx"

    /**
     * 教学进度。
     *
     * ⚠️ 这是本项目**唯一没接通的功能**：菜单里的「教学进度查询」由 JS 动态加载，
     * 静态 HTML 里拿不到地址，下面这些候选路径实测全部返回占位页。
     * 拿到真实入口地址后，替换这里并确认 [QzParser.parseProgress] 的选择器即可。
     */
    val PROGRESS_CANDIDATES = listOf(
        "$ROOT/jxgl/jxjd_list",
        "$ROOT/jxjd/jxjd_list",
        "$ROOT/kbcx/jxjd_list",
        "$ROOT/jxjh/jxjd_list",
        "$ROOT/jxgl/jxjdgl_list",
    )

    /** 成绩查询候选路径（强智标准版常见）。 */
    val GRADE_CANDIDATES = listOf(
        "$ROOT/kscj/cjcx_list",
        "$ROOT/kscj/cjcx_list_gr",
        "$ROOT/kscj/cjcx_list_xn",
        "$ROOT/kscj/cjcx_list?kksj=",
    )

    /** 考试安排候选路径。 */
    val EXAM_CANDIDATES = listOf(
        "$ROOT/kwgl/kscx_list",
        "$ROOT/kwgl/kscx_list_gr",
        "$ROOT/kwgl/kscx_list_xn",
    )

    /** 校园网内直连的教务系统根地址。 */
    const val CAMPUS_ORIGIN = "http://10.1.1.149"

    /** 校外 WebVPN 门户根地址。 */
    const val VPN_ORIGIN = "http://vpn.huhst.edu.cn:9900"
}
