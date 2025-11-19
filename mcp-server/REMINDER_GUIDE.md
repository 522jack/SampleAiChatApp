# Task Reminder MCP Tool

## Overview

The MCP server now includes a powerful task reminder system with automatic periodic notifications. This tool allows you to manage tasks through your chat application with persistent SQLite storage and automated summary reports.

## Features

- **Task Management**: Add, complete, list, and delete tasks through chat
- **Persistent Storage**: Tasks are stored in a SQLite database (reminders.db)
- **Periodic Notifications**: Automatic summaries every 60 seconds showing:
  - Active tasks (incomplete)
  - Tasks completed today
  - Overall statistics
- **Rich Formatting**: Clean, formatted output with emojis for better readability

## Available Tools

### 1. add_task
Add a new task to the reminder system.

**Parameters:**
- `title` (required): Task title
- `description` (optional): Additional task details

**Example:**
```
Add a task: "Review pull requests" with description "Check PRs from the team"
```

### 2. complete_task
Mark a task as completed.

**Parameters:**
- `id` (required): Task ID to complete

**Example:**
```
Complete task ID 1
```

### 3. list_tasks
Get a list of all tasks with optional filtering.

**Parameters:**
- `include_completed` (optional, default: true): Include completed tasks in the list

**Example:**
```
List all active tasks
```

### 4. get_task_summary
Get the current task summary report (same as the periodic notifications).

**Parameters:** None

**Example:**
```
Show me the task summary
```

### 5. delete_task
Permanently delete a task.

**Parameters:**
- `id` (required): Task ID to delete

**Example:**
```
Delete task ID 5
```

## Periodic Notifications

The reminder service automatically generates a summary report every 60 seconds. The summary includes:

- **Active Tasks**: Tasks that haven't been completed yet
- **Completed Today**: Tasks that were completed on the current day
- **Statistics**: Total tasks, active count, completed count

### Sample Notification

```
📋 Task Summary Report
━━━━━━━━━━━━━━━━━━━━

📌 Active Tasks (2):
   1. [ID: 1] Review pull requests
      📝 Check PRs from the team
   2. [ID: 2] Update documentation

✅ Completed Today (1):
   1. Fix bug in authentication
      ⏰ Completed at: 14:30

📊 Statistics:
   • Total tasks: 3
   • Active: 2
   • Completed: 1

⏰ Next update in 60s
```

## Database

Tasks are stored in `reminders.db` (SQLite database) with the following schema:

```sql
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    is_completed BOOLEAN DEFAULT 0
)
```

## Configuration

You can customize the notification interval by modifying the `ReminderService` constructor parameter:

```kotlin
val reminderService = ReminderService(
    dbPath = "reminders.db",           // Database file path
    notificationIntervalSeconds = 60    // Notification interval (default: 60 seconds)
)
```

## Usage Examples

### Adding Tasks
```
User: Add a task to buy groceries
Assistant: [Uses add_task tool]
✅ Task added successfully!

📌 Task Details:
   ID: 1
   Title: Buy groceries
   Created: 2025-11-19 12:30:45
```

### Listing Tasks
```
User: Show me all my tasks
Assistant: [Uses list_tasks tool]
📋 Task List
━━━━━━━━━━━━━━━━━━━━

📌 Active Tasks (2):
   [1] Buy groceries
       🕐 Created: 2025-11-19 12:30:45
   [2] Call dentist
       🕐 Created: 2025-11-19 13:15:20

Total tasks: 2
```

### Completing Tasks
```
User: Complete task 1
Assistant: [Uses complete_task tool]
✅ Task completed successfully!

📋 Task: Buy groceries
   Completed at: 2025-11-19 15:45:10

This task will now appear in the 'Completed Today' section of summaries.
```

## Integration

The reminder service is automatically initialized when the MCP server starts. It runs in the background and generates periodic summaries without any manual intervention.

The service is integrated into the MCP server alongside the weather tools, providing a comprehensive set of productivity features.

## Technical Details

- **Language**: Kotlin
- **Database**: SQLite (xerial:sqlite-jdbc:3.46.0.0)
- **Concurrency**: Kotlin Coroutines with StateFlow for notifications
- **Architecture**: Service-based design with clean separation of concerns