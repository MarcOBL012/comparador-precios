package pe.com.comparadorprecios.data

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ScanRepositoryTest {

    private fun apiThatFailsWith(code: Int): ScanApi = object : ScanApi {
        override suspend fun scan(request: ScanRequest): ScanResponse {
            throw HttpException(
                Response.error<ScanResponse>(
                    code,
                    "{}".toResponseBody("application/json".toMediaType()),
                )
            )
        }
    }

    @Test
    fun `401 produce ScanError Unauthorized`() = runTest {
        val repository = ScanRepository(apiThatFailsWith(401))
        try {
            repository.scan("uri")
            fail("debía lanzar ScanError.Unauthorized")
        } catch (e: ScanError.Unauthorized) {
            assertTrue(true)
        }
    }
}
