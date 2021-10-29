Send emails with Sitefox using [nbb](https://github.com/borkdude/nbb).

To test this out:

 1. Clone this repo.
 2. Go into `examples/send-email`.
 3. `npm i`
 4. `npm run send`

This will run `email-example.clj` using nbb, and you will see the test email results output.

This demo uses ethereal.email unless `SMTP_URL` is configured.
Results will be printed to the console and not sent.
