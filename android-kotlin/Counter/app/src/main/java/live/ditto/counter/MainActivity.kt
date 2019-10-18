package live.ditto.counter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import live.ditto.*
import live.ditto.android.DefaultAndroidDittoKitDependencies

class MainActivity : AppCompatActivity() {

    private var counterValue = 0

    private var ditto: DittoKit? = null
    private var defaultCollection: DittoCollection? = null;
    private var liveQuery: DittoSingleDocumentLiveQuery<Map<String, Any>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val androidDependencies = DefaultAndroidDittoKitDependencies(applicationContext)
        ditto = DittoKit(androidDependencies);
        ditto!!.setAccessLicense("INSERT_YOUR_KEY")
        ditto!!.start()

        defaultCollection = ditto!!.store.collection("default")

        try {
            defaultCollection!!.findByID("default").exec()
        } catch (e: DittoKitError) {
            defaultCollection!!.insert(
                mapOf(
                    "_id" to "default",
                    "value" to 0
                )
            )
            defaultCollection!!.findByID("default").update{ dittoMutableDocument ->
                dittoMutableDocument["value"].replaceWithCounter(true)
            }
        }

        findViewById<Button>(R.id.plus_button)
            .setOnClickListener {
                defaultCollection!!.findByID("default").update{ dittoMutableDocument ->
                    dittoMutableDocument["value"].increment(1.0)
                }
            }

        findViewById<Button>(R.id.minus_button)
            .setOnClickListener {
                defaultCollection!!.findByID("default").update{ dittoMutableDocument ->
                    dittoMutableDocument["value"].increment(-1.0)
                }
            }


        liveQuery =  defaultCollection!!.findByID("default").observeAndSubscribe { e ->
            val document = e.newDocument
            if (document != null) {
                runOnUiThread {
                    this.counterValue = document["value"].floatValue.toInt()
                    updateCounterTextView()
                }
            }

        }
    }

    private fun updateCounterTextView() {
        findViewById<TextView>(R.id.counter_text_view).text = counterValue.toString()
    }
}
