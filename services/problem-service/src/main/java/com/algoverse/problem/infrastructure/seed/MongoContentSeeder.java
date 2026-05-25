package com.algoverse.problem.infrastructure.seed;

import com.algoverse.problem.infrastructure.mongo.ProblemDocument;
import com.algoverse.problem.infrastructure.mongo.ProblemDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Idempotent startup seeder — inserts MongoDB content documents for the 10
 * canonical problems only if the collection is empty. Safe to run on every
 * restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoContentSeeder {

    private final ProblemDocumentRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (repository.count() > 0) {
            log.info("MongoDB problem_content collection already seeded — skipping.");
            return;
        }

        log.info("Seeding MongoDB problem_content collection with 10 canonical problems...");
        repository.saveAll(buildSeedData());
        log.info("MongoDB seed complete.");
    }

    // -----------------------------------------------------------------------
    // Seed data
    // -----------------------------------------------------------------------

    private List<ProblemDocument> buildSeedData() {
        return List.of(
            twoSum(),
            validAnagram(),
            climbingStairs(),
            binarySearch(),
            invertBinaryTree(),
            numberOfIslands(),
            validPalindrome(),
            containerWithMostWater(),
            maximumDepthOfBinaryTree(),
            houseRobber()
        );
    }

    private ProblemDocument twoSum() {
        return new ProblemDocument(
            "two-sum", "two-sum",
            """
            ## Two Sum

            Given an array of integers `nums` and an integer `target`, return *indices of the two numbers such that they add up to* `target`.

            You may assume that each input would have **exactly one solution**, and you may not use the *same* element twice.

            You can return the answer in any order.

            **Example 1:**
            ```
            Input:  nums = [2,7,11,15], target = 9
            Output: [0,1]
            ```

            **Constraints:**
            - `2 <= nums.length <= 10^4`
            - `-10^9 <= nums[i] <= 10^9`
            - `-10^9 <= target <= 10^9`
            - Only one valid answer exists.
            """,
            """
            ## Editorial

            ### Approach: Hash Map (O(n))

            Iterate over the array once. For each element `x`, check if `target - x`
            already exists in a hash map. If so, return `[map[target-x], i]`.
            Otherwise, store `{x: i}` in the map.

            **Time:** O(n)  **Space:** O(n)
            """,
            List.of(
                new ProblemDocument.TestCase("[2,7,11,15]\n9", "[0,1]", "nums[0] + nums[1] == 9"),
                new ProblemDocument.TestCase("[3,2,4]\n6", "[1,2]", "nums[1] + nums[2] == 6")
            ),
            List.of(
                new ProblemDocument.TestCase("[3,3]\n6", "[0,1]", null)
            ),
            Map.of(
                "java", """
                    class Solution {
                        public int[] twoSum(int[] nums, int target) {
                            // your code here
                        }
                    }
                    """,
                "python", """
                    class Solution:
                        def twoSum(self, nums: List[int], target: int) -> List[int]:
                            # your code here
                            pass
                    """,
                "javascript", """
                    /**
                     * @param {number[]} nums
                     * @param {number} target
                     * @return {number[]}
                     */
                    var twoSum = function(nums, target) {
                        // your code here
                    };
                    """
            )
        );
    }

    private ProblemDocument validAnagram() {
        return new ProblemDocument(
            "valid-anagram", "valid-anagram",
            """
            ## Valid Anagram

            Given two strings `s` and `t`, return `true` if `t` is an anagram of `s`, and `false` otherwise.

            An **anagram** is a word or phrase formed by rearranging the letters of a different word or phrase,
            using all the original letters exactly once.

            **Constraints:**
            - `1 <= s.length, t.length <= 5 * 10^4`
            - `s` and `t` consist of lowercase English letters.
            """,
            """
            ## Editorial

            ### Approach: Frequency Count (O(n))

            Count character frequencies in `s` and `t`. If all counts match, they are anagrams.

            **Time:** O(n)  **Space:** O(1) (fixed 26-letter alphabet)
            """,
            List.of(
                new ProblemDocument.TestCase("\"anagram\"\n\"nagaram\"", "true", null),
                new ProblemDocument.TestCase("\"rat\"\n\"car\"", "false", null)
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public boolean isAnagram(String s, String t) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def isAnagram(self, s: str, t: str) -> bool:\n        # your code here\n        pass",
                "javascript", "var isAnagram = function(s, t) {\n    // your code here\n};"
            )
        );
    }

    private ProblemDocument climbingStairs() {
        return new ProblemDocument(
            "climbing-stairs", "climbing-stairs",
            """
            ## Climbing Stairs

            You are climbing a staircase. It takes `n` steps to reach the top.

            Each time you can either climb `1` or `2` steps. In how many distinct ways can you climb to the top?

            **Constraints:**
            - `1 <= n <= 45`
            """,
            """
            ## Editorial

            ### Approach: Dynamic Programming / Fibonacci

            Let `dp[i]` = number of ways to reach step `i`.
            `dp[i] = dp[i-1] + dp[i-2]`

            This is exactly the Fibonacci sequence with `dp[1] = 1`, `dp[2] = 2`.

            **Time:** O(n)  **Space:** O(1)
            """,
            List.of(
                new ProblemDocument.TestCase("2", "2", "1+1, 2"),
                new ProblemDocument.TestCase("3", "3", "1+1+1, 1+2, 2+1")
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public int climbStairs(int n) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def climbStairs(self, n: int) -> int:\n        # your code here\n        pass",
                "javascript", "var climbStairs = function(n) {\n    // your code here\n};"
            )
        );
    }

    private ProblemDocument binarySearch() {
        return new ProblemDocument(
            "binary-search", "binary-search",
            """
            ## Binary Search

            Given an array of integers `nums` which is sorted in ascending order, and an integer `target`,
            write a function to search `target` in `nums`. If `target` exists, return its index. Otherwise, return `-1`.

            **Constraints:**
            - `1 <= nums.length <= 10^4`
            - `-10^4 < nums[i], target < 10^4`
            - All the integers in `nums` are **unique**.
            - `nums` is sorted in ascending order.
            """,
            """
            ## Editorial

            ### Approach: Classic Binary Search

            Maintain `lo` and `hi` pointers. At each step, compare `nums[mid]` with target.

            **Time:** O(log n)  **Space:** O(1)
            """,
            List.of(
                new ProblemDocument.TestCase("[-1,0,3,5,9,12]\n9", "4", null),
                new ProblemDocument.TestCase("[-1,0,3,5,9,12]\n2", "-1", null)
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public int search(int[] nums, int target) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def search(self, nums: List[int], target: int) -> int:\n        # your code here\n        pass",
                "javascript", "var search = function(nums, target) {\n    // your code here\n};"
            )
        );
    }

    private ProblemDocument invertBinaryTree() {
        return new ProblemDocument(
            "invert-binary-tree", "invert-binary-tree",
            """
            ## Invert Binary Tree

            Given the `root` of a binary tree, invert the tree, and return its root.

            **Constraints:**
            - The number of nodes in the tree is in the range `[0, 100]`.
            - `-100 <= Node.val <= 100`
            """,
            """
            ## Editorial

            ### Approach: Recursive DFS

            Swap left and right children at each node, then recurse.

            **Time:** O(n)  **Space:** O(h) where h is tree height
            """,
            List.of(
                new ProblemDocument.TestCase("[4,2,7,1,3,6,9]", "[4,7,2,9,6,3,1]", null)
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public TreeNode invertTree(TreeNode root) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def invertTree(self, root: Optional[TreeNode]) -> Optional[TreeNode]:\n        # your code here\n        pass",
                "javascript", "var invertTree = function(root) {\n    // your code here\n};"
            )
        );
    }

    private ProblemDocument numberOfIslands() {
        return new ProblemDocument(
            "number-of-islands", "number-of-islands",
            """
            ## Number of Islands

            Given an `m x n` 2D binary grid `grid` which represents a map of `'1'`s (land) and `'0'`s (water),
            return the number of islands.

            An **island** is surrounded by water and is formed by connecting adjacent lands horizontally or vertically.
            You may assume all four edges of the grid are all surrounded by water.

            **Constraints:**
            - `m == grid.length`, `n == grid[i].length`
            - `1 <= m, n <= 300`
            - `grid[i][j]` is `'0'` or `'1'`.
            """,
            """
            ## Editorial

            ### Approach: BFS / DFS flood-fill

            For each unvisited `'1'` cell, perform a BFS/DFS to mark all connected land cells as visited,
            incrementing the island count once per BFS/DFS call.

            **Time:** O(m*n)  **Space:** O(min(m,n)) for BFS queue
            """,
            List.of(
                new ProblemDocument.TestCase(
                    "[[\"1\",\"1\",\"1\",\"1\",\"0\"],[\"1\",\"1\",\"0\",\"1\",\"0\"],[\"1\",\"1\",\"0\",\"0\",\"0\"],[\"0\",\"0\",\"0\",\"0\",\"0\"]]",
                    "1", null
                )
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public int numIslands(char[][] grid) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def numIslands(self, grid: List[List[str]]) -> int:\n        # your code here\n        pass",
                "javascript", "var numIslands = function(grid) {\n    // your code here\n};"
            )
        );
    }

    private ProblemDocument validPalindrome() {
        return new ProblemDocument(
            "valid-palindrome", "valid-palindrome",
            """
            ## Valid Palindrome

            A phrase is a **palindrome** if, after converting all uppercase letters into lowercase letters
            and removing all non-alphanumeric characters, it reads the same forward and backward.

            Given a string `s`, return `true` if it is a palindrome, or `false` otherwise.

            **Constraints:**
            - `1 <= s.length <= 2 * 10^5`
            - `s` consists only of printable ASCII characters.
            """,
            """
            ## Editorial

            ### Approach: Two Pointers

            Use left and right pointers. Skip non-alphanumeric characters, then compare
            lowercased characters. If any mismatch, return false.

            **Time:** O(n)  **Space:** O(1)
            """,
            List.of(
                new ProblemDocument.TestCase("\"A man, a plan, a canal: Panama\"", "true", null),
                new ProblemDocument.TestCase("\"race a car\"", "false", null)
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public boolean isPalindrome(String s) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def isPalindrome(self, s: str) -> bool:\n        # your code here\n        pass",
                "javascript", "var isPalindrome = function(s) {\n    // your code here\n};"
            )
        );
    }

    private ProblemDocument containerWithMostWater() {
        return new ProblemDocument(
            "container-with-most-water", "container-with-most-water",
            """
            ## Container With Most Water

            You are given an integer array `height` of length `n`. There are `n` vertical lines drawn such that
            the two endpoints of the `i`th line are `(i, 0)` and `(i, height[i])`.

            Find two lines that together with the x-axis form a container, such that the container contains the most water.

            Return the maximum amount of water a container can store.

            **Constraints:**
            - `n == height.length`
            - `2 <= n <= 10^5`
            - `0 <= height[i] <= 10^4`
            """,
            """
            ## Editorial

            ### Approach: Two Pointers

            Start with the widest possible container (lo=0, hi=n-1). At each step, move the pointer
            with the shorter height inward (since moving the taller one can only decrease the area).

            **Time:** O(n)  **Space:** O(1)
            """,
            List.of(
                new ProblemDocument.TestCase("[1,8,6,2,5,4,8,3,7]", "49", null),
                new ProblemDocument.TestCase("[1,1]", "1", null)
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public int maxArea(int[] height) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def maxArea(self, height: List[int]) -> int:\n        # your code here\n        pass",
                "javascript", "var maxArea = function(height) {\n    // your code here\n};"
            )
        );
    }

    private ProblemDocument maximumDepthOfBinaryTree() {
        return new ProblemDocument(
            "maximum-depth-of-binary-tree", "maximum-depth-of-binary-tree",
            """
            ## Maximum Depth of Binary Tree

            Given the `root` of a binary tree, return its maximum depth.

            A binary tree's **maximum depth** is the number of nodes along the longest path from the
            root node down to the farthest leaf node.

            **Constraints:**
            - The number of nodes in the tree is in the range `[0, 10^4]`.
            - `-100 <= Node.val <= 100`
            """,
            """
            ## Editorial

            ### Approach: Recursive DFS

            `maxDepth(root) = 1 + max(maxDepth(left), maxDepth(right))`
            Base case: `maxDepth(null) = 0`

            **Time:** O(n)  **Space:** O(h)
            """,
            List.of(
                new ProblemDocument.TestCase("[3,9,20,null,null,15,7]", "3", null),
                new ProblemDocument.TestCase("[1,null,2]", "2", null)
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public int maxDepth(TreeNode root) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def maxDepth(self, root: Optional[TreeNode]) -> int:\n        # your code here\n        pass",
                "javascript", "var maxDepth = function(root) {\n    // your code here\n};"
            )
        );
    }

    private ProblemDocument houseRobber() {
        return new ProblemDocument(
            "house-robber", "house-robber",
            """
            ## House Robber

            You are a professional robber planning to rob houses along a street. Each house has a certain amount
            of money stashed, the only constraint stopping you from robbing each of them is that adjacent houses
            have security systems connected and **it will automatically contact the police if two adjacent houses
            were broken into on the same night**.

            Given an integer array `nums` representing the amount of money of each house,
            return the maximum amount of money you can rob tonight without alerting the police.

            **Constraints:**
            - `1 <= nums.length <= 100`
            - `0 <= nums[i] <= 400`
            """,
            """
            ## Editorial

            ### Approach: Dynamic Programming

            Let `dp[i]` = max money robbing up to house `i`.
            `dp[i] = max(dp[i-1], dp[i-2] + nums[i])`

            Can be optimized to O(1) space by keeping two variables.

            **Time:** O(n)  **Space:** O(1)
            """,
            List.of(
                new ProblemDocument.TestCase("[1,2,3,1]", "4", "Rob house 1 (1) and house 3 (3)"),
                new ProblemDocument.TestCase("[2,7,9,3,1]", "12", "Rob house 1 (2), 3 (9), 5 (1)")
            ),
            List.of(),
            Map.of(
                "java", "class Solution {\n    public int rob(int[] nums) {\n        // your code here\n    }\n}",
                "python", "class Solution:\n    def rob(self, nums: List[int]) -> int:\n        # your code here\n        pass",
                "javascript", "var rob = function(nums) {\n    // your code here\n};"
            )
        );
    }
}
