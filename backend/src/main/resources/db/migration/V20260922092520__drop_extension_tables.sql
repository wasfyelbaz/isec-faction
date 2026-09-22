-- The App Store is gone. Installing an extension meant uploading a JAR that the server
-- then loaded through a custom classloader and executed in-process: remote code execution
-- with an approval dialog in front of it. The two things anyone actually installed — the
-- checklist tables and the severity bar chart — are rendered in-process now and configured
-- per report template, so nothing is lost by removing the mechanism.
--
-- Dropped rather than left orphaned: the rows hold uploaded JAR bytes and encrypted
-- extension configuration, and keeping them would leave uploaded code sitting in the
-- database with nothing able to run it.
--
-- extension_log first: it carries the foreign key.
DROP TABLE IF EXISTS extension_log;
DROP TABLE IF EXISTS extension;
