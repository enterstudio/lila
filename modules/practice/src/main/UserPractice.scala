package lila.practice

import lila.study.{ Study, Chapter }

case class UserPractice(
    structure: PracticeStructure,
    progress: PracticeProgress) {

  import UserPractice._

  def progressOn(studyId: Study.Id) = {
    val chapterIds = structure.study(studyId).??(_.chapterIds)
    Completion(
      done = progress countDone chapterIds,
      total = chapterIds.size)
  }
}

case class UserStudy(
  practice: UserPractice,
  practiceStudy: PracticeStudy,
  chapters: List[Chapter.Metadata],
  study: Study.WithChapter)

case class Completion(done: Int, total: Int) {

  def percent = if (total == 0) 0 else done * 100 / total

  def complete = done >= total
}
