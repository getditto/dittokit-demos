//
//  TasksTableViewController.swift
//  ToDo
//
//  Created by Adam Fish on 9/18/19.
//  Copyright © 2019 DittoLive Incorporated. All rights reserved.
//

import UIKit
import DittoSyncKitSwift

class TasksTableViewController: UITableViewController {
    // These hold references to DittoSyncKit for easy access
    var ditto: DittoSyncKit!
    var store: DittoStore!
    var liveQuery: DittoLiveQuery?
    var collection: DittoCollection!

    // We need to format the task creation date into a UTC string
    var dateFormatter = ISO8601DateFormatter()

    // This is the UITableView data source
    var tasks: [DittoDocument] = []

    override func viewDidLoad() {
        super.viewDidLoad()

        // Create an instance of DittoSyncKit
        ditto = DittoSyncKit()

        // Set your DittoSyncKit access license
        // The SDK will not work without this!
        ditto.setAccessLicense("<INSERT ACCESS LICENSE>")

        // This starts DittoSyncKit's background synchronization
        ditto.start()

        // Create some helper variables for easy access
        store = ditto.store
        // We will store data in the "tasks" collection
        // DittoSyncKit stores data as collections of documents
        collection = store.collection("tasks")

        // This function will create a "live-query" that will update
        // our UITableView
        setupTaskList()
    }

    func setupTaskList() {
        // Query for all tasks and sort by dateCreated
        // Observe changes with a live-query and update the UITableView
        liveQuery = collection.findAll().sort("dateCreated", direction: .ascending).observe { [weak self] docs, event in
            guard let `self` = self else { return }
            switch event {
            case .update(let changes):
                guard changes.insertions.count > 0 || changes.deletions.count > 0 || changes.updates.count > 0  || changes.moves.count > 0 else { return }
                DispatchQueue.main.async {
                    self.tableView.beginUpdates()
                    self.tableView.performBatchUpdates({
                        let deletionIndexPaths = changes.deletions.map { idx -> IndexPath in
                            return IndexPath(row: idx, section: 0)
                        }
                        self.tableView.deleteRows(at: deletionIndexPaths, with: .automatic)
                        let insertionIndexPaths = changes.insertions.map { idx -> IndexPath in
                            return IndexPath(row: idx, section: 0)
                        }
                        self.tableView.insertRows(at: insertionIndexPaths, with: .automatic)
                        let updateIndexPaths = changes.updates.map { idx -> IndexPath in
                            return IndexPath(row: idx, section: 0)
                        }
                        self.tableView.reloadRows(at: updateIndexPaths, with: .automatic)
                        for move in changes.moves {
                            let from = IndexPath(row: move.from, section: 0)
                            let to = IndexPath(row: move.to, section: 0)
                            self.tableView.moveRow(at: from, to: to)
                        }
                    }) { _ in }
                    // Set the tasks array backing the UITableView to the new documents
                    self.tasks = docs
                    self.tableView.endUpdates()
                }
            case .initial:
                // Set the tasks array backing the UITableView to the new documents
                self.tasks = docs
                DispatchQueue.main.async {
                    self.tableView.reloadData()
                }
            default: break
            }
        }
    }

    @IBAction func didClickAddTask(_ sender: UIBarButtonItem) {
        // Create an alert
        let alert = UIAlertController(
            title: "Add New Task",
            message: nil,
            preferredStyle: .alert)

        // Add a text field to the alert for the new task text
        alert.addTextField(configurationHandler: nil)

        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel, handler: nil))

        // Add a "OK" button to the alert. The handler calls addNewToDoItem()
        alert.addAction(UIAlertAction(title: "OK", style: .default, handler: { [weak self] (_) in
            guard let `self` = self else { return }
            if let text = alert.textFields?[0].text
            {
                let dateString = self.dateFormatter.string(from: Date())
                // Insert the data into DittoSyncKit
                let _ = try! self.collection.insert([
                    "text": text,
                    "dateCreated": dateString,
                    "isComplete": false
                ])
            }
        }))

        // Present the alert to the user
        present(alert, animated: true, completion: nil)
    }

    // MARK: - Table view data source

    override func numberOfSections(in tableView: UITableView) -> Int {
        return 1
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return tasks.count
    }


    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "taskCell", for: indexPath)

        // Configure the cell...
        let task = tasks[indexPath.row]
        cell.textLabel?.text = task["text"].stringValue
        let taskComplete = task["isComplete"].boolValue
        if taskComplete {
            cell.accessoryType = .checkmark
        }
        else {
            cell.accessoryType = .none
        }

        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        // Deselect the row so it is not highlighted
        tableView.deselectRow(at: indexPath, animated: true)
        // Retrieve the task at the row selected
        let task = tasks[indexPath.row]
        // Update the task to mark completed
        collection.findByID(task.id).update({ (newTask) in
            newTask?["isComplete"].set(!task["isComplete"].boolValue)
        })
    }

    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool {
        // Return false if you do not want the specified item to be editable.
        return true
    }

    // Override to support editing the table view.
    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            // Retrieve the task at the row swiped
            let task = tasks[indexPath.row]
            // Delete the task from DittoSyncKit
            try! collection.findByID(task.id).remove()
        }
    }

}
