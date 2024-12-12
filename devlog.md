# Development Log

## [2024-12-03, 6:39 PM]
### Thoughts
- Just starting on Project 3 for CS4348. The project involves creating an interactive program to manage index files using a B-Tree structure. I'm going over the specifications in detail to fully understand the requirements and plan out the initial steps.
- One major consideration is ensuring that the program manages files correctly and handles edge cases like file overwrites, invalid input, and maintaining the B-Tree structure with a minimal degree of 10.
- The project will involve a lot of interaction with files, so I will need to carefully plan how to structure file handling and in-memory management of the B-Tree nodes.

### Plan
1. **Set up the GitHub repository**:
   - Create a new GitHub repository named `CS4348_Project3`.
   - Initialize the repository locally and link it to GitHub.
   - Add the `devlog.md` file to track my progress.
2. **Understand the project requirements**:
   - Carefully review the specifications to ensure I have a complete grasp of what needs to be implemented.
   - Pay special attention to the commands, file structure, and how the B-Tree should operate.
   - Plan for implementing file header and node structures based on the given formats.
3. **First Implementation Steps**:
   - Start by implementing the `create` command, which involves creating a new index file with the correct header.
   - Set up basic file handling to ensure compatibility with big-endian format.

### Reflection
- Feeling good about starting. Breaking down the project into smaller steps seems like the best way to approach it.
- Next session, I plan to focus on the `create` command and finalize the repository setup.

---

## [2024-12-04, 10:15 AM]
### Thoughts
- Began working on the `create` command. Managed to initialize a file with the correct header block that includes the magic number and placeholders for root and next block ID.
- Encountered a small issue with big-endian byte ordering, but resolved it by using `ByteBuffer` to ensure consistent conversion.
- The code is compiling and the initial structure for the B-Tree node class is in place, though not fully tested yet.

### Plan
1. **Validate the `create` functionality**:
   - Test by creating a new file and verifying its contents match the expected header format.
2. **Implement `open` command**:
   - Once the file is created, implement `open` to read the header and load the B-Tree structure into memory.
3. **Set up a basic traversal or print method**:
   - Although no data will exist yet, a simple verification step can ensure nodes are read correctly.

### Reflection
- Making steady progress. The file creation step was a good first milestone.
- Next, I need to ensure the `open` command works smoothly so I can start inserting and verifying actual keys.

---

## [2024-12-04, 8:50 PM]
### Thoughts
- Implemented the `open` command. Now I can open an existing file and verify the magic number, which confirms that the file is indeed a B-Tree index.
- Created a skeleton for the `print` command. While it doesn’t print anything meaningful yet (no keys inserted), it will be useful later.
- Started drafting the logic for the `insert` command. For now, I'll handle the simplest case: inserting the first key into an empty tree.

### Plan
1. **Test `open` thoroughly**:
   - Create a file using `create`, close the program, then reopen using `open` to ensure state is preserved.
2. **Begin `insert` command basic case**:
   - Insert one key-value pair into an empty tree and verify the root node is updated correctly.
3. **Ensure consistent file I/O**:
   - Make sure that after insertion, the file header updates the root ID and next block ID as needed.

### Reflection
- Happy with the progress today. Implementing `open` was straightforward and confirming file headers works as expected.
- Tomorrow, I’ll focus on the first insert and ensuring that the B-Tree structure updates properly on disk.

---

## [2024-12-05, 3:45 PM]
### Thoughts
- Implemented and tested the `open` command more thoroughly. The state seems consistent between sessions now.
- Added basic insertion logic. Inserting the first key into an empty B-Tree now creates a root node and writes it to the file.
- Encountered a subtle issue: ensuring that when I insert, I also update the header to reflect `root_id` and `next_block_id`. Fixed it by rewriting the header after a successful insert.

### Plan
1. **Insert multiple keys**:
   - Try inserting several keys into the root without splits. The limit before needing a split is 19 keys, so I’ll test with fewer than that.
2. **Implement LRU cache next**:
   - Before handling splits, ensure the LRU cache properly manages node reads/writes to reduce I/O overhead.
3. **Printing**:
   - After multiple inserts, update the `print` command to traverse and confirm keys are stored in ascending order.

### Reflection
- The first insertion worked! This is a major step.
- Next challenge is multiple insertions and eventually handling node splits.

---

## [2024-12-06, 11:20 AM]
### Thoughts
- Inserted multiple keys (1 through 10) successfully without splits. Verified using `print` that keys appear in ascending order.
- Began integrating the LRU cache. The cache now stores recently accessed nodes. Need to test eviction by creating more nodes than the cache capacity.
- Considering the logic for splits. I'll need a clear approach to implement `split_child` correctly.

### Plan
1. **LRU Cache Test**:
   - Insert enough keys to require multiple node operations. Although no splits yet, I can still test the cache by forcing node retrieval.
2. **Start `split_child` pseudo-code**:
   - Write out the steps for splitting a full node so that when I try to insert the 20th key, I know what to do.
3. **Incremental Testing**:
   - Always test after small changes to avoid introducing subtle bugs.

### Reflection
- Everything is still going smoothly. The structure is stable for simple cases.
- The LRU cache conceptually works, but I need more thorough testing.

---

## [2024-12-06, 9:05 PM]
### Thoughts
- Finalized initial LRU cache testing. Inserted 15 keys and confirmed that node retrieval from the file is reduced due to caching.
- Started writing the `split_child` method. I’m carefully handling the median key and making sure parent and children pointers remain correct.
- Next step: test `split_child` by inserting more than 19 keys to force the root to split.

### Plan
1. **Implement `split_child` fully**:
   - After coding the logic, I’ll insert 20 keys and see if a split occurs and if the B-Tree structure remains valid.
2. **Check node integrity after split**:
   - Verify that the `print` command lists all keys in ascending order, and `search` finds them correctly.
3. **Prepare `search` command**:
   - Once splits are stable, implement `search` to ensure I can find keys quickly.

### Reflection
- The project complexity is increasing, but the careful incremental approach helps.
- The next big milestone: successful node splitting and maintaining tree balance.

---

## [2024-12-07, 11:50 AM]
### Thoughts
- Implemented `split_child` and tested it by inserting 20 keys. The root split as expected, and the keys are distributed correctly.
- Implemented the `search` command. Searching for keys that exist and keys that don’t now returns the expected result.
- Considered adding more commands like `load` and `extract` to handle bulk operations.

### Plan
1. **Bulk Operations**:
   - Implement `load` to insert multiple keys from a file. This will be a good stress test for the insertion logic.
   - Implement `extract` to write out the current contents of the B-Tree to a file.
2. **Edge Case Testing**:
   - Search for keys less than any inserted key and greater than any inserted key.
   - Insert keys in descending order to ensure splits still happen correctly.

### Reflection
- Accomplished a major milestone with splitting and searching.
- Feeling more confident that the core B-Tree logic is solid.
- Time to expand functionality with `load` and `extract`.

---

## [2024-12-07, 8:25 PM]
### Thoughts
- Started working on `load`. It reads lines from a given file, parses keys and values, and inserts them. Handled invalid lines by skipping them.
- Began drafting `extract`. It will perform an in-order traversal and write all key-value pairs to a CSV-like file.
- Need to test `load` and `extract` extensively to ensure no corner cases break the logic (like empty files or invalid input).

### Plan
1. **Finish `load`**:
   - Insert a batch of keys from a test file with valid and invalid lines. Verify correct insertion and warnings on invalid lines.
2. **Finish `extract`**:
   - Extract currently stored keys to a file and confirm the output file matches the in-memory B-Tree structure.
3. **Final Round of Tests**:
   - Try various scenarios: empty tree, large number of keys, random key order, overwrite existing index file.

### Reflection
- The end is in sight. `load` and `extract` will make the program much more versatile.
- After these, I’ll focus on polishing and final detailed tests.

---

## [2024-12-08, 2:30 PM]
### Thoughts
- Completed the `load` and `extract` commands. `load` can now handle large batches of keys, and `extract` outputs them reliably.
- Conducted a thorough round of testing:
  - Inserted 30 keys, printed them, searched for several keys, extracted them to a file, and then loaded them back into another new index file.
  - Everything appears consistent and stable.

### Plan
1. **Code Cleanup and Documentation**:
   - Add more comments, clarify function names, and ensure error messages are user-friendly.
   - Update `README` and `devlog.md` files so they reflect the full development process.
2. **More Edge Cases**:
   - Try inserting zero keys and then extracting or searching.
   - Try loading a file with all invalid lines and ensure it handles gracefully.
3. **Prepare for Submission**:
   - Once done, I’ll finalize the project in the repository and ensure all required files are present and well-documented.

### Reflection
- The project is essentially complete with all core functionality working as intended.
- Learned a lot about careful B-Tree implementation, file I/O, and incremental testing.
- Confident in the solution’s reliability and readiness for submission.
