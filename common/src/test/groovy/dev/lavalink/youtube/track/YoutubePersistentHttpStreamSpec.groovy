package dev.lavalink.youtube.track

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpEntity
import spock.lang.Specification

class YoutubePersistentHttpStreamSpec extends Specification {

    def "should execute range transition seamlessly and reconnect when reading across chunk boundary"() {
        given: "a persistent stream with content length exceeding buffer chunk size"
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def contentUrl = new URI("https://manifest.googlevideo.com/videoplayback?expire=123")
        long bufferSize = 11862014
        long contentLength = 20000000 // Greater than bufferSize to force range segmentation
        long skipAmount = bufferSize - 4 // 11862010

        def response1 = Mock(ClassicHttpResponse)
        def entity1 = Mock(HttpEntity)
        def responseStream1 = Mock(InputStream)

        def stream = new YoutubePersistentHttpStream(httpInterface, contentUrl, contentLength)

        when: "we connect initially"
        stream.connect(false)

        then: "first request is executed for the initial range"
        1 * httpInterface.execute(_ as HttpUriRequestBase) >> { HttpUriRequestBase req ->
            assert req.getUri().toString().contains("range=0-" + bufferSize)
            response1.getCode() >> 200
            response1.getEntity() >> entity1
            entity1.getContent() >> responseStream1
            return response1
        }

        when: "we skip close to the end of the first chunk buffer"
        long skippedBytes = stream.skip(skipAmount)

        then: "skipping delegates to the underlying stream without creating a new connection"
        1 * responseStream1.skip(skipAmount) >> skipAmount
        skippedBytes == skipAmount
        stream.getPosition() == skipAmount

        when: "we read past the boundary of the current chunk"
        byte[] buffer = new byte[10]
        // nextExpectedPosition (11862010 + 10 + 5) will be 11862025 which is >= rangeEnd (11862014).
        // It should close connection, transition to the next range starting at 11862010, and read.
        int readBytes = stream.read(buffer, 0, 10)

        then: "reconnection is executed for the new range starting at the skipped position"
        1 * httpInterface.execute(_ as HttpUriRequestBase) >> { HttpUriRequestBase req ->
            // Range end is capped at contentLength because targetPosition + bufferSize exceeds it
            assert req.getUri().toString().contains("range=" + skipAmount + "-" + contentLength)
            
            def nextResponse = Mock(ClassicHttpResponse)
            def nextEntity = Mock(HttpEntity)
            def nextStream = new ByteArrayInputStream([1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as byte[])
            nextResponse.getCode() >> 206
            nextResponse.getEntity() >> nextEntity
            nextEntity.getContent() >> nextStream
            return nextResponse
        }

        and: "the read completes successfully returning the new bytes"
        readBytes == 10
        buffer == [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as byte[]
        stream.getPosition() == skipAmount + 10
    }
}
