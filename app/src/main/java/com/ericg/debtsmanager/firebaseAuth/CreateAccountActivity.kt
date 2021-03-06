package com.ericg.debtsmanager.firebaseAuth

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ericg.debtsmanager.AnalysisAndSettings
import com.ericg.debtsmanager.Debtors
import com.ericg.debtsmanager.R
import com.ericg.debtsmanager.communication.Contacts
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_create_account.*
import kotlinx.android.synthetic.main.dialog_report_issue.view.*

private var mAuth: FirebaseAuth? = null
private var mUser: FirebaseUser? = null
private var fDataBase: FirebaseFirestore? = null

private const val CHANNEL_ID = "Account Management "
private var NOTIFICATION_ID = 1

private var logInFailed = 0

@SuppressLint("InlinedApi")
val permissions = arrayOf(
    android.Manifest.permission.SEND_SMS,
    android.Manifest.permission.READ_CALENDAR,
    android.Manifest.permission.WRITE_CALENDAR,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.VIBRATE,
    android.Manifest.permission.WAKE_LOCK,
    android.Manifest.permission.SET_ALARM,
    android.Manifest.permission.READ_EXTERNAL_STORAGE,
    android.Manifest.permission.CAMERA,
    android.Manifest.permission.ACCESS_MEDIA_LOCATION,
    android.Manifest.permission.ACCESS_WIFI_STATE,
    android.Manifest.permission.CALL_PHONE
)

class CreateAccountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_create_account)

        handleClicks()
        createNotificationChannel()
        loadingStatus(false, btnEnabled = true)
        requestPermissions()
    }

    override fun onStart() {
        super.onStart()
        initialise()
    }

    private fun initialise() {
        mAuth = FirebaseAuth.getInstance()
        mUser = mAuth!!.currentUser
        fDataBase = FirebaseFirestore.getInstance()
    }

    fun requestPermissions() {
        ActivityCompat.requestPermissions(this@CreateAccountActivity,
            permissions, 5)
    }

    private fun createNotificationChannel() {
        // A registered channel is required for notifications in android 8.0 +

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Account management"
            val descriptionText = "Account settings"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val accountChannel = NotificationChannel(
                CHANNEL_ID, name, importance
            ).apply {
                description = descriptionText
            }

            // register this channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(accountChannel)
        }
    }

    private fun notifyAccountManagement(
        nTitle: String,
        nContentText: String,
        nPendingIntent: PendingIntent? = null
    ) {
        val accountManager = NotificationCompat.Builder(this,
            CHANNEL_ID
        )
        accountManager.apply {
            setSmallIcon(R.drawable.ic_dollar)
            title = nTitle
            color = getColor(R.color.colorOrange)
            setContentText(nContentText)
            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(nPendingIntent)
            setAutoCancel(false)
        }
        with(NotificationManagerCompat.from(this)) {
            notify(NOTIFICATION_ID, accountManager.build())
        }

        NOTIFICATION_ID += 1   // This allows more than one notification without cancelling the previous one(s)
    }

    @SuppressLint("InflateParams")
    private fun handleClicks() {

        aBtnLogIn.setOnClickListener {
            verifyInputs()
            logInFailed += 1

            when (logInFailed) {
                5 -> {
                    notifyAccountManagement(
                        "Having trouble ?",
                        "Try checking connection, filling required fields or reporting the issue",
                        null
                    )
                }

                15 -> {
                    notifyAccountManagement(
                        "Too many trials",
                        "Hey, contact us for help"
                    )
                }

            }
        }

        aBtnContacts.setOnClickListener {
            Contacts(this@CreateAccountActivity).contacts()
        }

        aBtnSignIn.setOnClickListener {
            val signInIntent = Intent(this, SignInActivity::class.java)
            if (signInIntent.resolveActivity(packageManager) != null) {
                startActivity(signInIntent)
                finish()
            } else {
                toast(this, "null intent")
            }
        }

        aBtnIssue.setOnClickListener {

            // todo() research onEditorActionListener ->
            //  issueDialogLayout.tvIssueDescriptionLength.text = rIssue.text.toString().length.toString()

            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {

                val issueDialog = AlertDialog.Builder(this)
                val issueDialogLayout: View =
                    layoutInflater.inflate(R.layout.dialog_report_issue, null)

                issueDialogLayout.rSendIssue.setOnClickListener {

                    val issueDescription = issueDialogLayout.rIssue.text.toString()
                    val mailSubject = "Debts manager create account issue"
                    val mailToAddress: Array<String> = arrayOf("debtsmanagercare@gmail.com")

                    if (issueDescription.trim().isNotEmpty()) {

                        Contacts(this).emailUs(mailSubject, mailToAddress, issueDescription)
                    } else {
                        toast(this, "can't send empty description")
                    }
                }
                issueDialog.apply {
                    setView(issueDialogLayout)

                    create().show()
                }
            } else {
                requestPermissions()
            }
        }
    }

    private fun passwordIsStrong(): Boolean {
        var isStrong = false
        val userPassword = aPassword.text.toString().trim()
        val userConfirmPassword = aConfirmPassword.text.toString().trim()
        val passwords = arrayOf(aPassword, aConfirmPassword)

        if (userPassword == userConfirmPassword && userConfirmPassword.isNotEmpty()) {
            if (
            // check password's length

                (userPassword.trim().length >= 8)
                &&

                // Check if password contains special character(s)

                ((userPassword.contains("@", true)) ||
                        (userPassword.contains("_", true)) ||
                        (userPassword.contains("#", true)) ||
                        (userPassword.contains("$", true)) ||
                        (userPassword.contains("%", true)) ||
                        (userPassword.contains("&", true)) ||
                        (userPassword.contains("*", true)) ||
                        (userPassword.contains("!", true)) ||
                        (userPassword.contains("?", true)))
                &&

                //check if password contains at least one Int

                ((userPassword.contains("0", true)) ||
                        (userPassword.contains("1", true)) ||
                        (userPassword.contains("2", true)) ||
                        (userPassword.contains("3", true)) ||
                        (userPassword.contains("4", true)) ||
                        (userPassword.contains("5", true)) ||
                        (userPassword.contains("6", true)) ||
                        (userPassword.contains("7", true)) ||
                        (userPassword.contains("8", true)) ||
                        (userPassword.contains("9", true)))

            ) {
                isStrong = true
            }

        } else if (userPassword != userConfirmPassword) {
            aPassword.error = "not matching"
            aConfirmPassword.error = "not matching"
        } else {
            for (password in passwords) {
                if (password.text.toString().trim().isEmpty()) {
                    password.error = "${password.hint} is required"
                }
            }
        }
        return isStrong
    }

    private fun checkTheEmpty(items: Array<TextInputEditText>) {
        for (item in items) {
            if (item.text.toString().trim().isEmpty()) {
                item.error = "${item.hint} is required"
            }
        }
    }

    private fun verifyInputs() {

        val userName = aUserName.text.toString().trim()
        val userEmail = aEmail.text.toString().trim()
        val userPhone = aPhone.text.toString().trim()
        val userPassword = aPassword.text.toString().trim()
        val userConfirmPassword = aConfirmPassword.text.toString().trim()

        val notEmpty: Boolean =
            (userName.isNotEmpty() && userEmail.isNotEmpty() && userPhone.isNotEmpty()
                    && userPassword.isNotEmpty() && userConfirmPassword.isNotEmpty())

        //val inputs = arrayOf(userName, userEmail, userPhone, userPassword, userConfirmPassword)
        val inputsIds = arrayOf(aUserName, aEmail, aPhone, aPassword, aConfirmPassword)

        fun saveUserData() {
            // todo -> save data to firestore which has offline support
        }

        val onTap = Intent(this, AnalysisAndSettings::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, onTap, 0
        )

        if (notEmpty && passwordIsStrong()) {
            loadingStatus(true, btnEnabled = false)
            mAuth!!
                .createUserWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        loadingStatus(false, btnEnabled = false)
                        sendVerificationEmail()
                        startActivity(Intent(this@CreateAccountActivity, Debtors::class.java))
                        saveUserData()

                        // todo save user has account to shared preferences

                        toast(this, "Welcome to Debts manager")
                        notifyAccountManagement(
                            "Congratulations $userName!",
                            "To manage your account, click analysis >> " +
                                    "settings then perform any changes you wish",
                            pendingIntent
                        )
                        finish()
                    } else if (task.isCanceled || !task.isSuccessful) {
                        toast(this, "Oops! an error occurred")
                        loadingStatus(false, btnEnabled = true)
                    }
                }

        } else if (!notEmpty) {
            checkTheEmpty(inputsIds)
        } else if (!passwordIsStrong() && userPassword == userConfirmPassword) {
            val warning = AlertDialog.Builder(this)
            warning.apply {
                setTitle(getString(R.string.warning))
                setIcon(getDrawable(R.drawable.ic_warning))
                setMessage("Use a more secure password")
                setPositiveButton("Got it") { _, _ -> }
                setCancelable(false)

                create().show()
            }
        }
    }

    fun toast(context: Activity, msg: String) {
        Toast.makeText(context, msg, LENGTH_LONG).show()
    }

    private fun loadingStatus(showLoading: Boolean, btnEnabled: Boolean) {

        if (showLoading) {
            aLoadingView.apply {
                setViewColor(getColor(R.color.colorGreen))
                setRoundColor(getColor(R.color.colorOrange))
                startAnim()
                visibility = View.VISIBLE
            }
            aLoading.visibility = View.VISIBLE
        } else {
            aLoadingView.apply {
                stopAnim()
                visibility = View.INVISIBLE
            }
            aLoading.visibility = View.INVISIBLE

        }

        if (btnEnabled) {
            buttonsStatus(true)
        } else {
            buttonsStatus(false)
        }
    }

    private fun buttonsStatus(enabled: Boolean) {
        val buttons = arrayOf(aBtnLogIn, aBtnContacts, aBtnSignIn, aBtnIssue)
        for (button in buttons) {
            button.isEnabled = enabled
        }
    }

    private fun sendVerificationEmail() {
        if (mUser != null) {
            Handler().postDelayed({
                mUser!!
                    .sendEmailVerification()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            toast(this, "Link sent to ${mUser!!.email}")
                        } else {
                            toast(this, "Sending failed!")
                        }
                    }
            }, 3000)
        } else {
            toast(this, "No user to send Email to!")
        }
    }

    private val backIsEnabled = false
    override fun onBackPressed() = if (backIsEnabled) {
        super.onBackPressed()
    } else {
        toast(this, "Use other buttons instead")
    }
}