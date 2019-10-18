//
//  ViewController.swift
//  Counter
//
//  Created by Maximilian Alexander on 9/29/19.
//  Copyright © 2019 Ditto. All rights reserved.
//

import UIKit
import DittoKit

class ViewController: UIViewController {
    
    @IBOutlet var plusButton: UIButton!
    @IBOutlet var minusButton: UIButton!
    @IBOutlet var valueLabel: UILabel!
    
    // ditto
    var ditto: DittoKit!
    var store: DittoStore!
    var liveQuery: DittoSingleDocumentLiveQuery<[String: Any?]>!
    var defaultCollection: DittoCollection!
    var diagnosticsTimer: Timer?
    
    var counterValue: Int = 0
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // Do any additional setup after loading the view.
        ditto = try! DittoKit()
        ditto.setAccessLicense("<INSERT_LICENSE>")
        
        ditto.start()
        defaultCollection = try! ditto.store.collection(name: "default")
        
        
        if (try! defaultCollection.findByID("default").exec() == nil) {
            try! defaultCollection.insert([
                "_id": "default",
                "value": 0
            ])
            try! defaultCollection.findByID("default").update({ (doc) in
                try! doc!["value"].replaceWithCounter()
            })
        }
        
        liveQuery = try? defaultCollection.findByID("default").observe { [weak self] (e) in
            DispatchQueue.main.sync {
                guard let `self` = self else { return }
                guard let doc = e.newDocument else {
                    return
                }
                self.counterValue = doc["value"].intValue
                self.updateValueLabel()
            }
        }
        
        runDiagnostics()
    }
    
    @IBAction @objc func plusButtonDidClick() {
        _ = try! defaultCollection.findByID("default").update({ (doc) in
            guard let doc = doc else { return }
            try! doc["value"].increment(amount: 1)
            try? doc["something"].set("foo")
        })
    }
    
    @IBAction @objc func minusButtonDidClick() {
        _ = try? defaultCollection.findByID("default").update({ (doc) in
            guard let doc = doc else { return }
            try? doc["value"].increment(amount: -1)
            try? doc["something"].set("bar")
        })
    }
    
    func updateValueLabel() {
        valueLabel.text = String(counterValue)
    }
    
    func runDiagnostics() {
        diagnosticsTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) {
            _ in
            if let diag = try? self.ditto.transportDiagnostics() {
                print("--- Diagnostics")
                for transport in diag.transports {
                    var out = "Transport \(transport.transportType): \(transport.condition)"
                    if !transport.connecting.isEmpty {
                        out += ", connecting:\(transport.connecting)"
                    }
                    if !transport.connected.isEmpty {
                        out += ", connected:\(transport.connected)"
                    }
                    if !transport.disconnecting.isEmpty {
                        out += ", disconnecting:\(transport.disconnecting)"
                    }
                    if !transport.disconnected.isEmpty {
                        out += ", disconnected:\(transport.disconnected)"
                    }
                    print(out)
                }
            } else {
                print("Error getting diagnostics")
            }
        }
    }
    
    
}

