# EntrantHub

[EntrantHub.com](https://entranthub.com) is an online community hub for tech enthusiasts, aggregating programming contests, hackathons, competitions, and more from multiple platforms into a single, unified experience.

Built with a tech stack emphasizing strong type safety and high concurrency.






## Features

- Unified Contest Aggregation: Track programming contests from Codeforces, LeetCode, AtCoder, and CodeChef in one place
- Hackathon Discovery: Browse and filter hackathons from DevPost
- Kaggle Competitions: Stay updated on active data science and ML competitions
- GSoC Explorer: Explore Google Summer of Code organizations and projects
- LeetCode Contest Analytics: View contest rankings, question statistics, and rating predictions
- Real-time Updates: Background schedulers keep data fresh from all platforms
- Responsive Design: Works seamlessly on desktop and mobile devices





## Tech Stack

- [JDK](https://openjdk.org/projects/jdk/) 21
- [Scala](https://scala-lang.org/) 3.7.2
- [Apache Pekko](https://pekko.apache.org/)
- [PostgreSQL](https://www.postgresql.org/) 15
- [Apache Kafka](https://kafka.apache.org/) 4.1






## Development

```shell
git clone git@github.com:baoliay2008/entranthub.git
cd entranthub

# configure environment
cp .env.example .env
# !!! edit .env with your database and Kafka configuration

# backend tests
sbt test

# run with sbt
sbt run

# or build a fat JAR
sbt assembly
java -jar target/scala-3.7.2/entrant.fyi-assembly-0.1.0.jar
```




## Roadmap

- [ ] Question difficulty rating via Maximum Likelihood Estimation
- [ ] User accounts
  - [ ] Custom event submissions
  - [ ] Follow other users






## License

> Note: This repository contains only **core backend** code under [AGPL-3.0](LICENSE).
