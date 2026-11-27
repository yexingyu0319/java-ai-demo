package com.example.leetcode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 四周冲刺计划里 Day 1 ~ Day 4 的 LeetCode 题目，四道一起。
 *
 * 所有方法都是 static（静态方法）—— 意思是"不用创建对象就能直接调用"，
 * 你写 LeetCode 时看到 class Solution { public int[] twoSum(...) } 也能直接跑。
 *
 * 代码里我写得比 LeetCode 上啰嗦一些，是为了让你看懂每一步，不是为了炫技。
 */
public class LeetCodeSolutions {

    // =====================================================================
    // 第 1 题：两数之和（Day 1）
    // 题目：给一个整数数组 nums 和一个目标值 target，找出【两个】数使它们相加等于 target，
    //       返回这两个数的下标（位置编号）。
    // 例子：nums = [2, 7, 11, 15], target = 9  → 答案 [0, 1]（因为 2 + 7 = 9）
    // =====================================================================
    public static int[] twoSum(int[] nums, int target) {
        // 准备一个"字典"（Map）：key 存"数字"，value 存"它的下标"
        // 想象成一排储物柜：柜门上的号码是数字，柜子里的纸条写着"这号码在第几个位置"
        Map<Integer, Integer> seen = new HashMap<>();

        // for 循环：从 0 开始，一直走到 nums 的最后一个位置
        // nums.length 就是这个数组有几个数，比如 [2,7,11,15] 的 length 是 4
        // i++ 的意思是"i 每次加 1"
        for (int i = 0; i < nums.length; i++) {

            int current = nums[i];          // 当前这个数字，比如第 0 个是 2
            int need = target - current;    // 要凑成 target，还差多少？9 - 2 = 7

            // 问字典：之前见过 7 吗？
            if (seen.containsKey(need)) {
                // 见过！那 need 的下标 + 当前下标，就是答案
                // new int[]{...} 是"现场造一个整数数组"的写法
                return new int[]{seen.get(need), i};
            }

            // 没见过，就把当前数字和它的位置记进字典，供后面的数字查询
            seen.put(current, i);
        }

        // 题目保证有解，正常走不到这里；但 Java 要求所有分支都 return，所以补一个空数组
        return new int[]{};
    }

    // =====================================================================
    // 第 20 题：有效的括号（Day 2）
    // 题目：只包含 () [] {} 的字符串，判断括号是否"正确配对、正确嵌套"。
    // 例子："()" → true；"(]" → false；"([)]" → false
    // =====================================================================
    public static boolean isValid(String s) {
        // 用"栈"（Stack）。栈就像一摞盘子：后放上去的，先拿走。
        // ArrayDeque 是 Java 里推荐的栈实现（比老的 Stack 快）
        Deque<Character> stack = new ArrayDeque<>();

        // toCharArray() 把字符串拆成一个一个字符，方便循环
        for (char c : s.toCharArray()) {

            if (c == '(') {
                // 遇到左圆括号，就在栈顶放一个"我期待的右括号"
                stack.push(')');
            } else if (c == '[') {
                stack.push(']');
            } else if (c == '{') {
                stack.push('}');
            } else {
                // 走到这里说明 c 是右括号。
                // 情况一：栈是空的，说明这个右括号没有对应的左括号 → 不合法
                // 情况二：栈顶不是它期待的右括号 → 不合法
                if (stack.isEmpty() || stack.pop() != c) {
                    return false;
                }
            }
        }

        // 循环结束后，所有括号都配对完，栈应该是空的。
        // 如果不是空的（比如 "(((" ），说明有左括号没闭合 → 不合法
        return stack.isEmpty();
    }

    // =====================================================================
    // 第 242 题：有效的字母异位词（Day 3）
    // 题目：两个字符串，如果它们由完全相同的字母、只是顺序不同，就返回 true。
    // 例子："anagram" 和 "nagaram" → true；"rat" 和 "car" → false
    // 思路：统计每个字母出现几次，两边的统计结果必须完全一样。
    // =====================================================================
    public static boolean isAnagram(String s, String t) {
        // 长度都不一样，肯定不是（比如 "ab" 和 "abc"）
        if (s.length() != t.length()) {
            return false;
        }

        // 造 26 个抽屉，分别代表 a~z，每个抽屉里放"这个字母出现了几次"
        // Java 的 int 数组默认全是 0，不用手动初始化
        int[] count = new int[26];

        // 遍历字符串的每个位置（0 到 length-1）
        for (int i = 0; i < s.length(); i++) {
            // s.charAt(i) 取第 i 个字符，比如 "rat".charAt(0) 是 'r'
            // 'r' - 'a' = 17，因为字符在计算机里是按顺序编号的，相减就得到"第几个字母"
            count[s.charAt(i) - 'a']++;    // s 里的字母：对应抽屉 +1
            count[t.charAt(i) - 'a']--;    // t 里的字母：对应抽屉 -1
        }

        // 如果两个字符串字母组成完全一样，所有抽屉最后都会是 0
        for (int c : count) {
            if (c != 0) {
                return false;
            }
        }
        return true;
    }

    // =====================================================================
    // 第 349 题：两个数组的交集（Day 4）
    // 题目：给两个数组，返回它们共同拥有的数字（结果不能有重复）。
    // 例子：[1,2,2,1] 和 [2,2] → [2]
    // 思路：把第一个数组塞进"集合"（Set，会自动去重），再拿第二个数组的每个数字去问"你在不在里面"。
    // =====================================================================
    public static int[] intersection(int[] nums1, int[] nums2) {
        // HashSet：像一个"只能放不同东西"的袋子，重复放同一个不会变多
        Set<Integer> set1 = new HashSet<>();
        for (int n : nums1) {
            set1.add(n);            // 重复的数字加进去也只留一个
        }

        // LinkedHashSet 和 HashSet 一样会去重，但它"记得放入顺序"，输出更稳定好看
        Set<Integer> result = new LinkedHashSet<>();
        for (int n : nums2) {
            if (set1.contains(n)) {   // 这个数字在第一个数组里出现过吗？
                result.add(n);        // 出现过就收集起来（重复的会被自动去重）
            }
        }

        // Set 不能直接返回，题目要求 int[]，所以转成数组
        int[] ans = new int[result.size()];
        int i = 0;
        for (int n : result) {
            ans[i] = n;
            i++;
        }
        return ans;
    }

    // =====================================================================
    // 第 704 题：二分查找（Day 5）
    // 题目：在一个【已经排序】的数组里找目标值，返回下标；找不到返回 -1。
    // 例子：nums = [-1,0,3,5,9,12], target = 9 → 4
    // 思路：每次取中间那个数比较，比目标小就往右找，比目标大就往左找，每次砍掉一半。
    // =====================================================================
    public static int search(int[] nums, int target) {
        int left = 0;                    // 左边界（下标）
        int right = nums.length - 1;     // 右边界（最后一个下标）

        // 注意这里是 <= 而不是 <：只剩一个元素时也必须进循环检查
        while (left <= right) {
            // 这么写而不是 (left + right) / 2，是为了防止两个大数相加溢出变成负数
            int mid = left + (right - left) / 2;

            if (nums[mid] == target) {
                return mid;              // 找到了
            } else if (nums[mid] < target) {
                left = mid + 1;          // 目标在右半边
            } else {
                right = mid - 1;         // 目标在左半边
            }
        }
        return -1;                       // 找完了，没有
    }

    // =====================================================================
    // 下面是"自动判题器"：把上面四个方法用 LeetCode 官方给出的样例跑一遍，
    // 打印每一条是否通过。你不需要看懂它，但它能证明代码是对的。
    // =====================================================================
    public static void main(String[] args) {
        int pass = 0;
        int total = 0;

        System.out.println("========== 第 1 题：两数之和 ==========");
        total += 3;
        pass += check("nums=[2,7,11,15], target=9", Arrays.toString(twoSum(new int[]{2, 7, 11, 15}, 9)), "[0, 1]");
        pass += check("nums=[3,2,4], target=6", Arrays.toString(twoSum(new int[]{3, 2, 4}, 6)), "[1, 2]");
        pass += check("nums=[3,3], target=6", Arrays.toString(twoSum(new int[]{3, 3}, 6)), "[0, 1]");

        System.out.println("\n========== 第 20 题：有效的括号 ==========");
        total += 6;
        pass += check("\"()\"", String.valueOf(isValid("()")), "true");
        pass += check("\"()[]{}\"", String.valueOf(isValid("()[]{}")), "true");
        pass += check("\"(]\"", String.valueOf(isValid("(]")), "false");
        pass += check("\"([)]\"", String.valueOf(isValid("([)]")), "false");
        pass += check("\"(((\"", String.valueOf(isValid("(((")), "false");
        pass += check("\"\"（空串）", String.valueOf(isValid("")), "true");

        System.out.println("\n========== 第 242 题：字母异位词 ==========");
        total += 3;
        pass += check("\"anagram\" vs \"nagaram\"", String.valueOf(isAnagram("anagram", "nagaram")), "true");
        pass += check("\"rat\" vs \"car\"", String.valueOf(isAnagram("rat", "car")), "false");
        pass += check("\"ab\" vs \"abc\"", String.valueOf(isAnagram("ab", "abc")), "false");

        System.out.println("\n========== 第 349 题：两个数组的交集 ==========");
        total += 2;
        pass += checkArr("[1,2,2,1] ∩ [2,2]", intersection(new int[]{1, 2, 2, 1}, new int[]{2, 2}), new int[]{2});
        pass += checkArr("[4,9,5] ∩ [9,4,9,8,4]", intersection(new int[]{4, 9, 5}, new int[]{9, 4, 9, 8, 4}), new int[]{4, 9});

        System.out.println("\n========== 第 704 题：二分查找 ==========");
        total += 5;
        int[] arr = {-1, 0, 3, 5, 9, 12};
        pass += check("nums=[-1,0,3,5,9,12], target=9", String.valueOf(search(arr, 9)), "4");
        pass += check("目标 2（不存在）", String.valueOf(search(arr, 2)), "-1");
        pass += check("目标 -1（第一个）", String.valueOf(search(arr, -1)), "0");
        pass += check("目标 12（最后一个）", String.valueOf(search(arr, 12)), "5");
        pass += check("单元素数组 [5], target=5", String.valueOf(search(new int[]{5}, 5)), "0");

        System.out.println("\n========================================");
        System.out.println("通过 " + pass + " / " + total);
        System.out.println("========================================");
    }

    /** 比较实际输出和期望输出，打印一行结果 */
    private static int check(String name, String actual, String expected) {
        boolean ok = actual.equals(expected);
        System.out.printf("%-32s 实际=%-12s 期望=%-12s %s%n",
                name, actual, expected, ok ? "✅" : "❌");
        return ok ? 1 : 0;
    }

    /**
     * 比较两个整数数组（先排序再比）。
     * 为什么不能直接比？因为交集的题目允许结果以任意顺序返回，
     * [4,9] 和 [9,4] 都是正确答案。排序后比较才公平。
     */
    private static int checkArr(String name, int[] actual, int[] expected) {
        int[] a = actual.clone();
        int[] e = expected.clone();
        Arrays.sort(a);
        Arrays.sort(e);
        boolean ok = Arrays.equals(a, e);
        System.out.printf("%-32s 实际=%-12s 期望=%-12s %s%n",
                name, Arrays.toString(actual), Arrays.toString(expected), ok ? "✅" : "❌");
        return ok ? 1 : 0;
    }
}