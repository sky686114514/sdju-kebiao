package com.kebiao.app.feature.import

/* =========================================================================
 * 「自动进入课表」的点击脚本
 *
 * ## 为什么需要点击（真机第四轮的现场）
 * 登录成功后 App 停在门户首页，而课表**不在首页上**：首页此刻 iframe 0 个、表格 0 个，
 * 课表入口藏在左上角「菜单」抽屉里（面包屑「学生全部服务 » 选课 » 我的课表」）。
 * 此前 `SCHEDULE_ENTRY_SCRIPT` 只**读取** href 且要求唯一候选 —— 抽屉未展开时
 * 一个候选都没有，于是用户只能自己摸进菜单。这一轮按用户要求补上"替用户点进去"。
 *
 * ## 安全铁律（硬，违反即退回）
 *  1. **每次调用最多点 1 个元素**，且必须是**可见**元素（有尺寸、非 display:none）；
 *  2. 只点文本命中的那个（精确优先，其次包含），文本长度上限 12 —— 防止点到
 *     把整页文本都算进去的容器（`body`/根 div 的 textContent 也含「课表」）；
 *  3. **绝不点**含 `登录/退出/注销/查询/导出/打印/删除` 字样的元素；
 *  4. **绝不触碰表单控件**（不读、不填、不提交 input/textarea/select）；
 *  5. 只读 DOM + 触发一次 click，不读 cookie/localStorage，不写任何存储；
 *  6. **页面上有密码框就一个都不点** —— 登录页兜底，上游判漏了也不出事。
 *
 * ## 产出（JSON 字符串）
 * ```
 * {"step":"entry"|"menu"|"expand"|"none","text":"我的课表","tag":"A","href":"…或 null"}
 * ```
 *  - `entry`：点中了课表入口（导航已发生，驱动端应停下等页面渲染）；
 *  - `menu`：没找到课表入口，改为点开了菜单/抽屉（驱动端应等一帧再试）；
 *  - `expand`：菜单里也没有，退而点开「选课」这类分组（再试下一轮）；
 *  - `none`：什么都没找到 —— **如实返回，不猜**。
 * ========================================================================= */

internal object ImportEntryClick {

    val SCRIPT: String = """
        (function () {
          // 归一化：去掉所有空白。教务页面里「我的 课表」「\n我的课表」都存在。
          function norm(s) { return String(s == null ? '' : s).replace(/\s+/g, ''); }

          function visible(el) {
            if (!el || !el.getBoundingClientRect) { return false; }
            var r = el.getBoundingClientRect();
            if (r.width < 2 || r.height < 2) { return false; }
            try {
              var st = window.getComputedStyle(el);
              if (!st || st.visibility === 'hidden' || st.display === 'none') { return false; }
              if (parseFloat(st.opacity || '1') < 0.05) { return false; }
            } catch (e) { }
            return true;
          }

          // 危险词：任何命中即整条排除。避开"点错就出事"的那一类元素。
          var DANGER = /登录|退出|注销|登出|查询|导出|打印|删除|退课|重置/;

          /**
           * 在可见、文本命中的元素里挑一个最可点的。
           * 打分：精确命中 > 包含命中；A/BUTTON > role=button/link > 带 onclick > 其他；
           * 面积越小越优先（越可能是那个按钮本身，而不是包住它的容器）。
           */
          function pick(names, maxLen, allowContains) {
            var nodes = document.querySelectorAll(
              'a,button,[role="button"],[role="link"],[onclick],li,span,div,p,td'
            );
            var best = null;
            for (var i = 0; i < nodes.length; i++) {
              var el = nodes[i];
              if (!visible(el)) { continue; }
              var t = norm(el.textContent);
              if (!t || t.length > maxLen) { continue; }
              if (DANGER.test(t)) { continue; }
              var score = -1, k;
              for (k = 0; k < names.length; k++) {
                if (t === names[k]) { score = 100 - k * 5; break; }
              }
              if (score < 0 && allowContains) {
                for (k = 0; k < names.length; k++) {
                  if (t.indexOf(names[k]) >= 0) { score = 50 - k * 5; break; }
                }
              }
              if (score < 0) { continue; }
              var tag = (el.tagName || '').toUpperCase();
              if (tag === 'A' || tag === 'BUTTON') { score += 20; }
              else if (el.getAttribute && el.getAttribute('role') &&
                       /button|link/.test(el.getAttribute('role'))) { score += 10; }
              else if (el.onclick || (el.getAttribute && el.getAttribute('onclick'))) { score += 8; }
              var r = el.getBoundingClientRect();
              score -= Math.min(20, Math.round((r.width * r.height) / 5000));
              if (!best || score > best.score) {
                best = { el: el, score: score, text: t, tag: tag };
              }
            }
            return best;
          }

          function fire(el) {
            try { el.click(); return true; } catch (e) {
              try {
                ['mousedown', 'mouseup', 'click'].forEach(function (type) {
                  el.dispatchEvent(new MouseEvent(type, {
                    bubbles: true, cancelable: true, view: window
                  }));
                });
                return true;
              } catch (e2) { return false; }
            }
          }

          function out(step, hit) {
            var href = null;
            try { href = hit && hit.el && hit.el.getAttribute ? hit.el.getAttribute('href') : null; }
            catch (e) { href = null; }
            return JSON.stringify({
              step: step,
              text: hit ? hit.text : null,
              tag: hit ? hit.tag : null,
              href: href
            });
          }

          // 总闸：页面上有密码框就一个都不点。
          // 登录页上出现任何"看着像课表/菜单"的可点元素都不该由我们来点 —— 用户此刻
          // 还在输账号密码。这条是兜底：即使上游的 isPortalUrl 判漏了也不出事。
          var inputs = document.querySelectorAll('input');
          for (var i = 0; i < inputs.length; i++) {
            if (String(inputs[i].getAttribute('type') || '').toLowerCase() === 'password') {
              return out('none', null);
            }
          }

          // ① 课表入口本身（精确优先）
          var entry = pick(['我的课表', '我的课程表', '学生课表', '我的课表(学生)', '课表'], 12, true);
          if (entry && fire(entry.el)) { return out('entry', entry); }

          // ② 菜单 / 抽屉触发器（真机截图左上角就是「菜单」）
          var menu = pick(['菜单', 'menu', '导航', '全部服务', '学生全部服务'], 10, true);
          if (menu && fire(menu.el)) { return out('menu', menu); }

          // ③ 分组（菜单展开后课表常挂在「选课」下面）。
          // 刻意不含「全部」：它是子串匹配且太泛，非门户页上任何 ≤8 字含「全部」的
          // 可见元素都可能被点到。
          var group = pick(['选课', '学生服务', '我的服务'], 8, true);
          if (group && fire(group.el)) { return out('expand', group); }

          return out('none', null);
        })()
    """.trimIndent()
}
