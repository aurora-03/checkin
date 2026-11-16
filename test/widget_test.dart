import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:daily_check/main.dart';
import 'package:daily_check/models.dart';

void main() {
  test('备份保留小数、日记和归档状态，拒绝损坏数据', () {
    final h = Habit(
      id: '1',
      title: '阅读',
      unit: '页',
      dailyTarget: 20,
      goal: 600,
      archived: true,
    );
    h.entries['2026-11-14'] = const DailyEntry(12.5, '今天读到喜欢的段落');
    final restored = decodeHabits(encodeHabits([h])).single;
    expect(restored.total, 12.5);
    expect(restored.entries['2026-11-14']!.note, '今天读到喜欢的段落');
    expect(restored.archived, true);
    expect(
      () => decodeHabits('{"version":2,"habits":[]}'),
      throwsFormatException,
    );
    expect(() => decodeHabits(encodeHabits([h, h])), throwsFormatException);
    expect(
      () => decodeHabits(encodeHabits([h]).replaceFirst('12.5', '-1')),
      throwsFormatException,
    );
  });
  test('连续达标支持跨月、今天未打卡以及中断', () {
    final h = Habit(id: '1', title: '运动');
    h.entries['2026-10-31'] = const DailyEntry(1, '');
    h.entries['2026-11-01'] = const DailyEntry(1, '');
    expect(h.streak(DateTime(2026, 11, 2)), 2);
    h.entries['2026-11-02'] = const DailyEntry(.5, '');
    expect(h.streak(DateTime(2026, 11, 2)), 2);
    expect(h.streak(DateTime(2026, 11, 3)), 0);
  });
  testWidgets('创建任务、打卡、写日记并在重新打开后保留数据', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const CheckInApp());
    await tester.pumpAndSettle();
    expect(find.text('种下你的第一个小目标'), findsOneWidget);
    await tester.tap(find.text('新建任务'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextFormField).at(0), '阅读');
    await tester.ensureVisible(find.text('保存任务'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('保存任务'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('打卡'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('打卡'));
    await tester.pumpAndSettle();
    expect(find.text('✓ 已完成'), findsOneWidget);
    await tester.tap(find.text('编辑记录'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextFormField).at(1), '读完第一章');
    await tester.ensureVisible(find.text('保存记录'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('保存记录'));
    await tester.pumpAndSettle();
    final prefs = await SharedPreferences.getInstance();
    final saved = decodeHabits(prefs.getString('daily_check_v1')!).single;
    expect(saved.entries[dayKey(DateTime.now())]!.note, '读完第一章');
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(const CheckInApp());
    await tester.pumpAndSettle();
    expect(find.text('阅读'), findsOneWidget);
    await tester.ensureVisible(find.text('✓ 已完成'));
    await tester.pumpAndSettle();
    expect(find.text('✓ 已完成'), findsOneWidget);
    await tester.tap(find.text('记录').last);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.textContaining('读完第一章'),
      180,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('读完第一章'), findsOneWidget);
  });
  testWidgets('窄屏能显示任务并打开编辑器', (tester) async {
    tester.view.physicalSize = const Size(320, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    SharedPreferences.setMockInitialValues({
      'daily_check_v1': encodeHabits([
        Habit(
          id: 'narrow',
          title: '每天坚持阅读自己喜欢的书籍',
          unit: '页',
          dailyTarget: 20,
          goal: 600,
        ),
      ]),
    });
    await tester.pumpWidget(const CheckInApp());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('新建任务'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('保存任务'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
