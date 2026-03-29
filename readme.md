A VASL extension for playing ASL PBS (Play Both Sides)
https://www.facebook.com/groups/358520268920548

### build the class
`javac --release 11 -d out -cp /home/nicola/fun-dev/vasl/target/classes:/home/nicola/fun-dev/vassal/vassal-app/target/classes src/ASLPBSChecker.java 
`

### build the class and deploy
`javac --release 11 -d /home/nicola/fun-dev/asl_pbs/out -cp /home/nicola/fun-dev/vasl/target/classes:/home/nicola/fun-dev/vassal/vassal-app/target/classes /home/nicola/fun-dev/asl_pbs/src/ASLPBSChecker.java && cd /home/nicola/fun-dev/asl_pbs/out && zip -u /home/nicola/Jottacloud/Vassal/vasl-6.7.2-beta4_ext/asl_pbs.vmdx VASL/build/module/ASLPBSChecker*.class`
